package com.cardbilling.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExternalStatementLineTest {

    private static ExternalStatementLine line() {
        return ExternalStatementLine.ingested("EXT-1", "52998224725", 125_000L,
                LocalDate.of(2026, 3, 10), "EXT-1,52998224725,125000,2026-03-10");
    }

    @Test
    void isUnmatchedUntilItIsMatched() {
        ExternalStatementLine line = line();
        assertThat(line.isMatched()).isFalse();

        line.markMatched();

        assertThat(line.isMatched()).isTrue();
    }

    @Test
    void hasNoIdentityUntilItIsStored() {
        assertThat(line().getId()).isNull();
        assertThat(line().withId(99L).getId()).isEqualTo(99L);
    }

    @Test
    void refusesALineWithoutADocumentNumber() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> ExternalStatementLine.ingested("EXT-1", "", 1L, LocalDate.now(), "raw"))
                .withMessageContaining("document_number");
    }

    @Test
    void refusesANegativeAmount() {
        assertThatExceptionOfType(MalformedStatementLineException.class)
                .isThrownBy(() -> ExternalStatementLine.ingested("EXT-1", "529", -5L, LocalDate.now(), "raw"))
                .withMessageContaining("must be positive");
    }
}
