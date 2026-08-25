package com.cardbilling.reconciliation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.cardbilling.reconciliation.application.fake.FakeBillingService;
import com.cardbilling.reconciliation.application.fake.InMemoryReconciliationMatchRepository;
import com.cardbilling.reconciliation.application.fake.InMemoryReconciliationRunRepository;
import com.cardbilling.reconciliation.application.fake.InMemoryStatementLineRepository;
import com.cardbilling.reconciliation.application.port.BillingServiceUnavailableException;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import com.cardbilling.reconciliation.domain.InvoiceCandidate;
import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import com.cardbilling.reconciliation.domain.ReconciliationRun;
import com.cardbilling.reconciliation.domain.ReconciliationRunAlreadyInProgressException;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchStatementUseCaseTest {

    private static final String DOCUMENT = "52998224725";
    private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 3, 10);
    private static final long AMOUNT_CENTS = 125_000L;

    private final InMemoryStatementLineRepository statementLines = new InMemoryStatementLineRepository();
    private final InMemoryReconciliationRunRepository runs = new InMemoryReconciliationRunRepository();
    private final InMemoryReconciliationMatchRepository matches = new InMemoryReconciliationMatchRepository();
    private final FakeBillingService billingService = new FakeBillingService();

    private final MatchStatementUseCase useCase =
            new MatchStatementUseCase(statementLines, runs, matches, billingService);

    private ExternalStatementLine givenUnmatchedLine(String reference, long amountCents) {
        return statementLines.save(
                ExternalStatementLine.ingested(reference, DOCUMENT, amountCents, STATEMENT_DATE, "raw"));
    }

    @Test
    @DisplayName("an invoice owing exactly the statement amount is paid and the line is settled")
    void recordsAPaymentForAnExactMatch() {
        givenUnmatchedLine("EXT-1", AMOUNT_CENTS);
        billingService.withInvoice(new InvoiceCandidate(42L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE.minusDays(2)));

        ReconciliationRun run = useCase.run();

        assertThat(billingService.payments()).singleElement().satisfies(payment -> {
            assertThat(payment.invoiceId()).isEqualTo(42L);
            assertThat(payment.amountCents()).isEqualTo(AMOUNT_CENTS);
            assertThat(payment.externalReference()).isEqualTo("EXT-1");
            assertThat(payment.paidAt()).isEqualTo(STATEMENT_DATE.atStartOfDay());
        });
        assertThat(statementLines.all()).singleElement()
                .matches(ExternalStatementLine::isMatched, "is marked matched");
        assertThat(matches.all()).singleElement().satisfies(match -> {
            assertThat(match.getResult()).isEqualTo(ReconciliationMatch.Result.MATCHED);
            assertThat(match.getInvoiceId()).isEqualTo(42L);
            assertThat(match.getRunId()).isEqualTo(run.getId());
        });
        assertThat(run.getStatus()).isEqualTo(ReconciliationRun.Status.COMPLETED);
        assertThat(run.getTotalLines()).isEqualTo(1);
        assertThat(run.getMatchedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an invoice in the window owing something else is a divergence, and is not paid")
    void recordsADivergenceWithoutPaying() {
        givenUnmatchedLine("EXT-1", AMOUNT_CENTS);
        billingService.withInvoice(new InvoiceCandidate(42L, DOCUMENT, AMOUNT_CENTS + 4_200L, STATEMENT_DATE));

        ReconciliationRun run = useCase.run();

        assertThat(billingService.payments()).isEmpty();
        assertThat(matches.all()).singleElement().satisfies(match -> {
            assertThat(match.getResult()).isEqualTo(ReconciliationMatch.Result.DIVERGENT_AMOUNT);
            assertThat(match.getInvoiceId()).isNull();
        });
        assertThat(statementLines.all()).singleElement()
                .matches(line -> !line.isMatched(), "stays unmatched for a human to look at");
        assertThat(run.getDivergentCount()).isEqualTo(1);
        assertThat(run.getUnmatchedCount()).isZero();
    }

    @Test
    void recordsNotFoundWhenTheCustomerHasNoInvoiceInTheWindow() {
        givenUnmatchedLine("EXT-1", AMOUNT_CENTS);
        billingService.withInvoice(new InvoiceCandidate(42L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE.plusDays(30)));

        ReconciliationRun run = useCase.run();

        assertThat(billingService.payments()).isEmpty();
        assertThat(matches.all()).singleElement()
                .extracting(ReconciliationMatch::getResult)
                .isEqualTo(ReconciliationMatch.Result.NOT_FOUND);
        assertThat(run.getUnmatchedCount()).isEqualTo(1);
        assertThat(run.getDivergentCount()).isZero();
    }

    @Test
    @DisplayName("the whole point: lookups scale with statement lines, not with invoices")
    void asksBillingServiceOncePerLineInsteadOfScanningEveryInvoice() {
        IntStream.range(0, 50).forEach(i -> givenUnmatchedLine("EXT-" + i, AMOUNT_CENTS));
        IntStream.range(0, 500).forEach(i -> billingService.withInvoice(
                new InvoiceCandidate(1000L + i, "OTHER-" + i, AMOUNT_CENTS, STATEMENT_DATE)));
        IntStream.range(0, 50).forEach(i -> billingService.withInvoice(
                new InvoiceCandidate(i, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE)));

        ReconciliationRun run = useCase.run();

        assertThat(run.getMatchedCount()).isEqualTo(50);
        // 50 lines, one indexed lookup each. The legacy would have compared 50 x 550 pairs.
        assertThat(billingService.searchInvoiceCalls()).isEqualTo(50);
        assertThat(billingService.searchByDocumentCalls()).isZero();
    }

    @Test
    @DisplayName("a line that does not match costs one extra lookup, to tell a divergence from a miss")
    void asksASecondTimeOnlyWhenTheFirstLookupFoundNothing() {
        givenUnmatchedLine("EXT-1", AMOUNT_CENTS);

        useCase.run();

        assertThat(billingService.searchInvoiceCalls()).isEqualTo(1);
        assertThat(billingService.searchByDocumentCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("more lines than fit in one page are all reconciled")
    void worksThroughEveryPageOfUnmatchedLines() {
        int lineCount = MatchStatementUseCase.PAGE_SIZE * 2 + 13;
        IntStream.range(0, lineCount).forEach(i -> givenUnmatchedLine("EXT-" + i, AMOUNT_CENTS));
        billingService.withInvoice(new InvoiceCandidate(42L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE));

        ReconciliationRun run = useCase.run();

        assertThat(run.getTotalLines()).isEqualTo(lineCount);
        assertThat(run.getMatchedCount()).isEqualTo(lineCount);
        assertThat(billingService.searchInvoiceCalls()).isEqualTo(lineCount);
    }

    @Test
    @DisplayName("lines already matched by an earlier run are left alone")
    void reRunningOnlyLooksAtWhatIsStillUnmatched() {
        givenUnmatchedLine("EXT-1", AMOUNT_CENTS);
        billingService.withInvoice(new InvoiceCandidate(42L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE));
        useCase.run();

        ReconciliationRun secondRun = useCase.run();

        assertThat(secondRun.getTotalLines()).isZero();
        assertThat(billingService.payments()).hasSize(1);
    }

    @Test
    void refusesToStartASecondRunWhileOneIsStillRunning() {
        runs.save(ReconciliationRun.started());

        assertThatExceptionOfType(ReconciliationRunAlreadyInProgressException.class)
                .isThrownBy(useCase::run)
                .satisfies(e -> assertThat(e.getRunInProgressId()).isEqualTo(1L));
        assertThat(runs.all()).hasSize(1);
    }

    @Test
    @DisplayName("billing-service going down mid-run leaves the run FAILED, not silently COMPLETED")
    void marksTheRunFailedWhenBillingServiceStopsAnswering() {
        IntStream.range(0, 3).forEach(i -> givenUnmatchedLine("EXT-" + i, AMOUNT_CENTS));
        billingService.withInvoice(new InvoiceCandidate(42L, DOCUMENT, AMOUNT_CENTS, STATEMENT_DATE))
                .failingAfter(1, new BillingServiceUnavailableException("billing-service is down"));

        assertThatExceptionOfType(BillingServiceUnavailableException.class).isThrownBy(useCase::run);

        assertThat(runs.all()).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(ReconciliationRun.Status.FAILED);
            assertThat(run.getFinishedAt()).isNotNull();
        });
        // What it did manage before the outage stays committed, so a re-run picks up the rest.
        assertThat(billingService.payments()).hasSize(1);
        assertThat(statementLines.countUnmatched()).isEqualTo(2);
    }
}
