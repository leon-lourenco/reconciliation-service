package com.cardbilling.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.reconciliation.domain.ReconciliationMatch.Result;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The matching rule is the one piece of the legacy that had to survive the rewrite untouched, so
 * these tests pin the exact behaviour: same document, amount equal, statement date within three
 * days of the due date - in either direction.
 */
class StatementMatchRuleTest {

    private static final String DOCUMENT = "52998224725";
    private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 3, 10);
    private static final long AMOUNT_CENTS = 125_000L;

    private final ExternalStatementLine line = ExternalStatementLine.ingested(
            "EXT-1", DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE, "raw");

    private static InvoiceCandidate invoice(String documentNumber, long owedCents, LocalDate dueDate) {
        return new InvoiceCandidate(42L, documentNumber, owedCents, dueDate);
    }

    @ParameterizedTest(name = "due date {0} days from the statement date still matches")
    @ValueSource(ints = {-3, -2, -1, 0, 1, 2, 3})
    @DisplayName("a due date within three days either side of the statement date is a candidate")
    void acceptsDueDatesInsideTheThreeDayWindow(int daysOffset) {
        InvoiceCandidate invoice = invoice(DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE.plusDays(daysOffset));

        assertThat(StatementMatchRule.isExactMatchFor(invoice, line)).isTrue();
    }

    @ParameterizedTest(name = "due date {0} days from the statement date is outside the window")
    @ValueSource(ints = {-4, 4, 10})
    void rejectsDueDatesOutsideTheThreeDayWindow(int daysOffset) {
        InvoiceCandidate invoice = invoice(DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE.plusDays(daysOffset));

        assertThat(StatementMatchRule.isCandidateFor(invoice, line)).isFalse();
    }

    @Test
    void rejectsAnotherCustomersInvoiceEvenWhenAmountAndDateLineUp() {
        InvoiceCandidate someoneElse = invoice("11144477735", AMOUNT_CENTS, STATEMENT_DATE);

        assertThat(StatementMatchRule.isCandidateFor(someoneElse, line)).isFalse();
    }

    @Test
    void classifiesAnInvoiceOwingExactlyTheStatementAmountAsMatched() {
        List<InvoiceCandidate> invoices = List.of(invoice(DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE.minusDays(2)));

        assertThat(StatementMatchRule.classify(invoices, line)).isEqualTo(Result.MATCHED);
    }

    @Test
    @DisplayName("an invoice in the window owing a different amount is a divergence, not a miss")
    void classifiesADifferentAmountInsideTheWindowAsDivergent() {
        List<InvoiceCandidate> invoices = List.of(invoice(DOCUMENT, AMOUNT_CENTS + 1_000L, STATEMENT_DATE));

        assertThat(StatementMatchRule.classify(invoices, line)).isEqualTo(Result.DIVERGENT_AMOUNT);
        assertThat(StatementMatchRule.hasDivergentAmount(invoices, line)).isTrue();
    }

    @Test
    void classifiesNoInvoicesAtAllAsNotFound() {
        assertThat(StatementMatchRule.classify(List.of(), line)).isEqualTo(Result.NOT_FOUND);
    }

    @Test
    @DisplayName("an invoice outside the window never counts as a divergence")
    void ignoresInvoicesOutsideTheWindowWhenLookingForDivergence() {
        List<InvoiceCandidate> outsideWindow = List.of(
                invoice(DOCUMENT, AMOUNT_CENTS + 500L, STATEMENT_DATE.plusDays(9)));

        assertThat(StatementMatchRule.classify(outsideWindow, line)).isEqualTo(Result.NOT_FOUND);
    }

    @Test
    @DisplayName("an exact match wins even when a divergent invoice is listed first")
    void prefersTheExactMatchOverADivergentCandidate() {
        InvoiceCandidate divergent = new InvoiceCandidate(1L, DOCUMENT, AMOUNT_CENTS + 700L, STATEMENT_DATE);
        InvoiceCandidate exact = new InvoiceCandidate(2L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE.plusDays(1));

        assertThat(StatementMatchRule.firstExactMatch(List.of(divergent, exact), line))
                .contains(exact);
        assertThat(StatementMatchRule.classify(List.of(divergent, exact), line)).isEqualTo(Result.MATCHED);
    }

    @Test
    @DisplayName("one statement line settles exactly one invoice, as in the legacy")
    void takesTheFirstOfSeveralExactMatches() {
        InvoiceCandidate first = new InvoiceCandidate(7L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE);
        InvoiceCandidate second = new InvoiceCandidate(8L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE);

        assertThat(StatementMatchRule.firstExactMatch(List.of(first, second), line)).contains(first);
    }
}
