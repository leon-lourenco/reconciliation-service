package com.cardbilling.reconciliation.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import com.cardbilling.reconciliation.domain.MalformedStatementLineException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenCsvStatementLineParserTest {

    private static final String HEADER = "external_reference,document_number,amount_cents,statement_date\n";

    private final OpenCsvStatementLineParser parser = new OpenCsvStatementLineParser();
    private final List<ExternalStatementLine> parsed = new ArrayList<>();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsEveryDataRowAndSkipsTheHeader() {
        long rows = parser.parse(csv(HEADER
                + "EXT-1,52998224725,125000,2026-03-10\n"
                + "EXT-2,11144477735,98000,2026-03-11\n"), parsed::add);

        assertThat(rows).isEqualTo(2);
        assertThat(parsed).extracting(ExternalStatementLine::getExternalReference).containsExactly("EXT-1", "EXT-2");
        assertThat(parsed.getFirst().getAmountCents()).isEqualTo(125_000L);
        assertThat(parsed.getFirst().getStatementDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(parsed.getFirst().getRawLine()).isEqualTo("EXT-1,52998224725,125000,2026-03-10");
    }

    @Test
    void readsAFileThatHasNoHeaderRow() {
        long rows = parser.parse(csv("EXT-1,52998224725,125000,2026-03-10\n"), parsed::add);

        assertThat(rows).isEqualTo(1);
        assertThat(parsed).hasSize(1);
    }

    @Test
    void ignoresBlankLines() {
        long rows = parser.parse(csv(HEADER
                + "EXT-1,52998224725,125000,2026-03-10\n"
                + "\n"
                + "EXT-2,11144477735,98000,2026-03-11\n"
                + "\n"), parsed::add);

        assertThat(rows).isEqualTo(2);
    }

    @Test
    @DisplayName("rows are handed over as they are read, not after the whole file has been loaded")
    void streamsRowsInsteadOfReadingTheFileWhole() {
        // The failure is on the third data row. A parser that read the file into a list first - the
        // legacy's CSVReader.readAll() - would blow up having delivered nothing at all; this one has
        // already handed over the two good rows before it hits the bad one.
        InputStream file = csv(HEADER
                + "EXT-1,52998224725,125000,2026-03-10\n"
                + "EXT-2,11144477735,98000,2026-03-11\n"
                + "EXT-3,22233344455,not-a-number,2026-03-12\n"
                + "EXT-4,33344455566,50000,2026-03-13\n");

        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> parser.parse(file, parsed::add))
                .withMessageContaining("line 4")
                .withMessageContaining("amount_cents");

        assertThat(parsed).extracting(ExternalStatementLine::getExternalReference)
                .containsExactly("EXT-1", "EXT-2");
    }

    @Test
    void reportsTheFileLineNumberOfARowWithTooFewColumns() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> parser.parse(csv(HEADER + "EXT-1,52998224725\n"), parsed::add))
                .withMessageContaining("line 2")
                .withMessageContaining("expected 4 columns");
    }

    @Test
    void readsNothingFromAnEmptyFile() {
        assertThat(parser.parse(csv(""), parsed::add)).isZero();
        assertThat(parsed).isEmpty();
    }

    @Test
    void readsAFileWithOnlyAHeader() {
        assertThat(parser.parse(csv(HEADER), parsed::add)).isZero();
        assertThat(parsed).isEmpty();
    }
}
