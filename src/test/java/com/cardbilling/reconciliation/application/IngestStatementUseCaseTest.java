package com.cardbilling.reconciliation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.reconciliation.application.fake.FakeStatementLineParser;
import com.cardbilling.reconciliation.application.fake.InMemoryStatementLineRepository;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestStatementUseCaseTest {

    private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 3, 10);
    private static final InputStream IGNORED_FILE = new ByteArrayInputStream(new byte[0]);

    private final InMemoryStatementLineRepository statementLines = new InMemoryStatementLineRepository();

    private static ExternalStatementLine line(String reference) {
        return ExternalStatementLine.ingested(reference, "52998224725", 125_000L, STATEMENT_DATE, "raw");
    }

    private IngestStatementUseCase useCaseFor(List<ExternalStatementLine> parsedLines) {
        return new IngestStatementUseCase(new FakeStatementLineParser(parsedLines), statementLines);
    }

    @Test
    void storesEveryLineTheParserHandsOver() {
        IngestReport report = useCaseFor(List.of(line("EXT-1"), line("EXT-2"), line("EXT-3")))
                .ingest(IGNORED_FILE);

        assertThat(report).isEqualTo(new IngestReport(3, 3, 0));
        assertThat(statementLines.all()).extracting(ExternalStatementLine::getExternalReference)
                .containsExactly("EXT-1", "EXT-2", "EXT-3");
        assertThat(statementLines.all()).allMatch(line -> !line.isMatched());
    }

    @Test
    @DisplayName("re-uploading a file skips what is already stored instead of failing the upload")
    void skipsExternalReferencesAlreadyIngested() {
        useCaseFor(List.of(line("EXT-1"), line("EXT-2"))).ingest(IGNORED_FILE);

        IngestReport secondUpload = useCaseFor(List.of(line("EXT-1"), line("EXT-2"), line("EXT-3")))
                .ingest(IGNORED_FILE);

        assertThat(secondUpload).isEqualTo(new IngestReport(3, 1, 2));
        assertThat(statementLines.all()).hasSize(3);
    }

    @Test
    void skipsAReferenceRepeatedInsideTheSameFile() {
        IngestReport report = useCaseFor(List.of(line("EXT-1"), line("EXT-1"), line("EXT-2")))
                .ingest(IGNORED_FILE);

        assertThat(report).isEqualTo(new IngestReport(3, 2, 1));
        assertThat(statementLines.all()).extracting(ExternalStatementLine::getExternalReference)
                .containsExactly("EXT-1", "EXT-2");
    }

    @Test
    @DisplayName("a file larger than one batch is written in batches, never held whole")
    void writesInBoundedBatches() {
        int lineCount = IngestStatementUseCase.BATCH_SIZE * 2 + 37;
        List<ExternalStatementLine> parsedLines = new ArrayList<>();
        IntStream.range(0, lineCount).forEach(i -> parsedLines.add(line("EXT-" + i)));

        IngestReport report = useCaseFor(parsedLines).ingest(IGNORED_FILE);

        assertThat(report.ingested()).isEqualTo(lineCount);
        assertThat(statementLines.saveAllBatchSizes())
                .containsExactly(IngestStatementUseCase.BATCH_SIZE, IngestStatementUseCase.BATCH_SIZE, 37);
        assertThat(statementLines.saveAllBatchSizes())
                .allMatch(size -> size <= IngestStatementUseCase.BATCH_SIZE);
    }

    @Test
    void reportsNothingForAnEmptyFile() {
        assertThat(useCaseFor(List.of()).ingest(IGNORED_FILE)).isEqualTo(new IngestReport(0, 0, 0));
        assertThat(statementLines.all()).isEmpty();
    }
}
