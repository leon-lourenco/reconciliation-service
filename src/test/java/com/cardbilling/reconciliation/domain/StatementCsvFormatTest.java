package com.cardbilling.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementCsvFormatTest {

    @Test
    void readsARowIntoAStatementLine() {
        String[] row = {"EXT-000123", "52998224725", "125000", "2026-03-10"};

        ExternalStatementLine line = StatementCsvFormat.parse(row, 2);

        assertThat(line.getExternalReference()).isEqualTo("EXT-000123");
        assertThat(line.getCustomerDocumentNumber()).isEqualTo("52998224725");
        assertThat(line.getAmountCents()).isEqualTo(125_000L);
        assertThat(line.getStatementDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(line.getRawLine()).isEqualTo("EXT-000123,52998224725,125000,2026-03-10");
        assertThat(line.isMatched()).isFalse();
    }

    @Test
    void trimsSurroundingWhitespace() {
        ExternalStatementLine line = StatementCsvFormat.parse(
                new String[] {" EXT-1 ", " 52998224725", "125000 ", " 2026-03-10"}, 2);

        assertThat(line.getExternalReference()).isEqualTo("EXT-1");
        assertThat(line.getAmountCents()).isEqualTo(125_000L);
    }

    @Test
    void recognisesTheHeaderRow() {
        assertThat(StatementCsvFormat.isHeaderRow(StatementCsvFormat.HEADER)).isTrue();
        assertThat(StatementCsvFormat.isHeaderRow(new String[] {"EXT-1", "529", "1", "2026-03-10"})).isFalse();
    }

    @Test
    @DisplayName("the header is the legacy's, column for column")
    void keepsTheLegacyColumnContract() {
        assertThat(StatementCsvFormat.HEADER)
                .containsExactly("external_reference", "document_number", "amount_cents", "statement_date");
    }

    @Test
    void rejectsARowWithTheWrongNumberOfColumns() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> StatementCsvFormat.parse(new String[] {"EXT-1", "52998224725"}, 4))
                .withMessageContaining("line 4")
                .withMessageContaining("expected 4 columns, found 2");
    }

    @Test
    void rejectsAnAmountThatIsNotAWholeNumber() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> StatementCsvFormat.parse(
                        new String[] {"EXT-1", "52998224725", "1250.00", "2026-03-10"}, 3))
                .withMessageContaining("amount_cents");
    }

    @Test
    void rejectsADateThatIsNotIso() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> StatementCsvFormat.parse(
                        new String[] {"EXT-1", "52998224725", "125000", "10/03/2026"}, 3))
                .withMessageContaining("statement_date");
    }

    @Test
    void rejectsARowWithoutAnExternalReference() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> StatementCsvFormat.parse(
                        new String[] {"  ", "52998224725", "125000", "2026-03-10"}, 5))
                .withMessageContaining("line 5")
                .withMessageContaining("external_reference");
    }

    @Test
    void rejectsANonPositiveAmount() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> StatementCsvFormat.parse(
                        new String[] {"EXT-1", "52998224725", "0", "2026-03-10"}, 6))
                .withMessageContaining("must be positive");
    }
}
