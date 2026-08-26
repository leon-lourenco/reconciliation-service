package com.cardbilling.reconciliation.infrastructure.csv;

import com.cardbilling.reconciliation.application.port.StatementLineParserPort;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import com.cardbilling.reconciliation.domain.MalformedStatementLineException;
import com.cardbilling.reconciliation.domain.StatementCsvFormat;
import com.opencsv.CSVReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Reads a statement file with OpenCSV, one row at a time.
 *
 * <p>The legacy's ingest job called {@link CSVReader#readAll()}, which builds a list of every row
 * in the file before a single one is stored - fine for a demo file, not fine for a real one. This
 * iterates the reader instead, so the file is consumed as a stream and the caller decides how much
 * to hold. That difference is what the test on this class actually asserts.
 */
@Component
public class OpenCsvStatementLineParser implements StatementLineParserPort {

    @Override
    public long parse(InputStream csv, Consumer<ExternalStatementLine> onLine) {
        long rowNumber = 0;
        long dataRows = 0;
        try (CSVReader reader = new CSVReader(
                new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8)))) {
            for (String[] row : reader) {
                rowNumber++;
                if (isBlank(row)) {
                    continue;
                }
                if (rowNumber == 1 && StatementCsvFormat.isHeaderRow(row)) {
                    continue;
                }
                onLine.accept(StatementCsvFormat.parse(row, rowNumber));
                dataRows++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded statement file", e);
        } catch (RuntimeException e) {
            if (e instanceof MalformedStatementLineException) {
                throw e;
            }
            // OpenCSV wraps its own parse failures in a RuntimeException from the iterator.
            throw new MalformedStatementLineException("line " + rowNumber + ": " + e.getMessage());
        }
        return dataRows;
    }

    private static boolean isBlank(String[] row) {
        return row.length == 0 || (row.length == 1 && row[0].isBlank());
    }
}
