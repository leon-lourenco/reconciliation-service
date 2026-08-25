package com.cardbilling.reconciliation.application;

import com.cardbilling.reconciliation.application.port.BillingServicePort;
import com.cardbilling.reconciliation.application.port.ReconciliationMatchRepositoryPort;
import com.cardbilling.reconciliation.application.port.ReconciliationRunRepositoryPort;
import com.cardbilling.reconciliation.application.port.StatementLineRepositoryPort;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import com.cardbilling.reconciliation.domain.InvoiceCandidate;
import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import com.cardbilling.reconciliation.domain.ReconciliationMatch.Result;
import com.cardbilling.reconciliation.domain.ReconciliationRun;
import com.cardbilling.reconciliation.domain.ReconciliationRunAlreadyInProgressException;
import com.cardbilling.reconciliation.domain.StatementMatchRule;
import java.util.List;
import java.util.Optional;

/**
 * Works through every unmatched statement line and decides what each one is.
 *
 * <p>This is the fix the whole service exists for. The legacy did:
 *
 * <pre>
 *   for (line : unmatchedLines)          // outer loop
 *       for (invoice : allOpenInvoices)  // inner loop, every invoice, every time
 * </pre>
 *
 * <p>O(lines × invoices), and it had to be, because an external bank statement and this system's
 * invoices share no identifier - there was nothing to index a lookup on. billing-service now
 * answers "is there an invoice for this document, this amount, around this date" against a real
 * index, so the inner loop is gone: one indexed lookup per line, and this service holds one
 * statement line and one query result at a time instead of the whole invoice table.
 *
 * <p>Unmatched lines are read in bounded pages for the same reason, so a run's memory does not
 * grow with the number of lines waiting to be matched.
 *
 * <p>Deliberately not one big transaction, unlike the legacy job: a run makes an HTTP call per
 * line, and holding a database transaction open across all of them would keep a connection and
 * its locks for the length of the run. Each line's own writes are committed as they happen, which
 * also means a run that dies halfway leaves the lines it did match already marked - re-running is
 * safe, and billing-service's idempotency on the external reference covers the rest.
 */
public class MatchStatementUseCase {

    /** How many unmatched lines are fetched per page. */
    static final int PAGE_SIZE = 200;

    private final StatementLineRepositoryPort statementLines;
    private final ReconciliationRunRepositoryPort runs;
    private final ReconciliationMatchRepositoryPort matches;
    private final BillingServicePort billingService;

    public MatchStatementUseCase(StatementLineRepositoryPort statementLines, ReconciliationRunRepositoryPort runs,
            ReconciliationMatchRepositoryPort matches, BillingServicePort billingService) {
        this.statementLines = statementLines;
        this.runs = runs;
        this.matches = matches;
        this.billingService = billingService;
    }

    public ReconciliationRun run() {
        runs.findInProgress().ifPresent(inProgress -> {
            throw new ReconciliationRunAlreadyInProgressException(inProgress.getId());
        });

        ReconciliationRun run = runs.save(ReconciliationRun.started());
        Tally tally = new Tally();

        try {
            Long cursor = null;
            while (true) {
                List<ExternalStatementLine> page = statementLines.findUnmatchedAfter(cursor, PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                for (ExternalStatementLine line : page) {
                    tally.count(reconcile(run, line));
                }
                cursor = page.get(page.size() - 1).getId();
            }
        } catch (RuntimeException e) {
            run.fail();
            runs.save(run);
            throw e;
        }

        run.complete(tally.total, tally.matched, tally.notFound, tally.divergent);
        return runs.save(run);
    }

    private Result reconcile(ReconciliationRun run, ExternalStatementLine line) {
        Optional<InvoiceCandidate> exactMatch = billingService.searchInvoice(
                line.getCustomerDocumentNumber(), line.getAmountCents(), line.getStatementDate());

        if (exactMatch.isPresent() && StatementMatchRule.isExactMatchFor(exactMatch.get(), line)) {
            InvoiceCandidate invoice = exactMatch.get();
            // The statement line's own reference is the payment's idempotency key on
            // billing-service, so replaying a run cannot pay the same invoice twice.
            billingService.recordPayment(invoice.invoiceId(), line.getAmountCents(),
                    line.getExternalReference(), line.getStatementDate().atStartOfDay());
            line.markMatched();
            statementLines.save(line);
            matches.save(ReconciliationMatch.matched(run.getId(), line.getId(), invoice.invoiceId()));
            return Result.MATCHED;
        }

        // Nothing owing exactly this amount. One more indexed lookup, without the amount, tells a
        // discrepancy (an invoice in the window owing something else) apart from a line that is
        // simply not ours.
        List<InvoiceCandidate> sameCustomerInWindow = billingService.searchInvoicesByDocument(
                line.getCustomerDocumentNumber(), line.getStatementDate());

        if (StatementMatchRule.hasDivergentAmount(sameCustomerInWindow, line)) {
            matches.save(ReconciliationMatch.divergentAmount(run.getId(), line.getId()));
            return Result.DIVERGENT_AMOUNT;
        }
        matches.save(ReconciliationMatch.notFound(run.getId(), line.getId()));
        return Result.NOT_FOUND;
    }

    private static final class Tally {
        private int total;
        private int matched;
        private int divergent;
        private int notFound;

        private void count(Result result) {
            total++;
            switch (result) {
                case MATCHED -> matched++;
                case DIVERGENT_AMOUNT -> divergent++;
                case NOT_FOUND -> notFound++;
            }
        }
    }
}
