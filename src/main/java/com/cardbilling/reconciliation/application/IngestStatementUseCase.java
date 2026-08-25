package com.cardbilling.reconciliation.application;

import com.cardbilling.reconciliation.application.port.StatementLineParserPort;
import com.cardbilling.reconciliation.application.port.StatementLineRepositoryPort;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads an uploaded external statement into this service's own store, unmatched, for a later
 * reconciliation run to work through.
 *
 * <p>Two things the legacy job did are deliberately not done here:
 *
 * <ul>
 *   <li>It read the whole file into a list first ({@code CSVReader.readAll()}), so memory grew
 *       with the file. This streams: the parser hands over one line at a time and only a bounded
 *       batch is ever held, whatever the file's size.
 *   <li>It had no guard against re-ingesting a file, so the unique constraint on
 *       {@code external_reference} aborted the entire run on the first repeat rather than skipping
 *       it. Here an already-seen reference is skipped and counted, which makes re-uploading the
 *       same file a no-op instead of a failure.
 * </ul>
 */
public class IngestStatementUseCase {

    /** How many lines are held before they are written and released. */
    static final int BATCH_SIZE = 500;

    private final StatementLineParserPort parser;
    private final StatementLineRepositoryPort statementLines;

    public IngestStatementUseCase(StatementLineParserPort parser, StatementLineRepositoryPort statementLines) {
        this.parser = parser;
        this.statementLines = statementLines;
    }

    public IngestReport ingest(InputStream csv) {
        List<ExternalStatementLine> batch = new ArrayList<>(BATCH_SIZE);
        Tally tally = new Tally();

        long rowsRead = parser.parse(csv, line -> {
            batch.add(line);
            if (batch.size() >= BATCH_SIZE) {
                flush(batch, tally);
            }
        });
        flush(batch, tally);

        return new IngestReport(rowsRead, tally.ingested, tally.skipped);
    }

    private void flush(List<ExternalStatementLine> batch, Tally tally) {
        if (batch.isEmpty()) {
            return;
        }
        // Last occurrence of a reference repeated inside this batch wins, same as re-uploading it.
        Map<String, ExternalStatementLine> byReference = new LinkedHashMap<>();
        for (ExternalStatementLine line : batch) {
            byReference.put(line.getExternalReference(), line);
        }

        Set<String> alreadyStored = statementLines.findExistingExternalReferences(byReference.keySet());
        List<ExternalStatementLine> fresh = byReference.values().stream()
                .filter(line -> !alreadyStored.contains(line.getExternalReference()))
                .toList();

        statementLines.saveAll(fresh);

        tally.ingested += fresh.size();
        tally.skipped += batch.size() - fresh.size();
        batch.clear();
    }

    private static final class Tally {
        private long ingested;
        private long skipped;
    }
}
