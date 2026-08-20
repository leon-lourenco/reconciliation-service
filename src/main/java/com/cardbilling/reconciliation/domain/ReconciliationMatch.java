package com.cardbilling.reconciliation.domain;

import java.time.LocalDateTime;

/**
 * The outcome of matching one external statement line against billing-service's invoices, within
 * one run.
 *
 * <p>The legacy held a JPA reference to the {@code Invoice} it matched, because invoices lived in
 * the same database. They no longer do - {@code invoiceId} here is billing-service's identifier,
 * carried as a plain value with no foreign key, which is the whole point of the split.
 */
public final class ReconciliationMatch {

    public enum Result {
        MATCHED, DIVERGENT_AMOUNT, NOT_FOUND
    }

    private final Long id;
    private final Long runId;
    private final Long statementLineId;
    private final Long invoiceId;
    private final Result result;
    private final LocalDateTime matchedAt;

    private ReconciliationMatch(Long id, Long runId, Long statementLineId, Long invoiceId, Result result,
            LocalDateTime matchedAt) {
        this.id = id;
        this.runId = runId;
        this.statementLineId = statementLineId;
        this.invoiceId = invoiceId;
        this.result = result;
        this.matchedAt = matchedAt;
    }

    /** An invoice was found whose owed amount equals the statement line's amount. */
    public static ReconciliationMatch matched(Long runId, Long statementLineId, long invoiceId) {
        return new ReconciliationMatch(null, runId, statementLineId, invoiceId, Result.MATCHED, LocalDateTime.now());
    }

    /**
     * An invoice for this customer falls inside the date window, but no invoice owes exactly what
     * the statement says was paid - a real discrepancy for a human to look at, not a miss.
     */
    public static ReconciliationMatch divergentAmount(Long runId, Long statementLineId) {
        return new ReconciliationMatch(null, runId, statementLineId, null, Result.DIVERGENT_AMOUNT,
                LocalDateTime.now());
    }

    /** No invoice at all for this customer inside the date window. */
    public static ReconciliationMatch notFound(Long runId, Long statementLineId) {
        return new ReconciliationMatch(null, runId, statementLineId, null, Result.NOT_FOUND, LocalDateTime.now());
    }

    /** Rebuilds a match already stored by this service - used by the persistence adapter only. */
    public static ReconciliationMatch rehydrate(Long id, Long runId, Long statementLineId, Long invoiceId,
            Result result, LocalDateTime matchedAt) {
        return new ReconciliationMatch(id, runId, statementLineId, invoiceId, result, matchedAt);
    }

    public ReconciliationMatch withId(Long assignedId) {
        return new ReconciliationMatch(assignedId, runId, statementLineId, invoiceId, result, matchedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getStatementLineId() {
        return statementLineId;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public Result getResult() {
        return result;
    }

    public LocalDateTime getMatchedAt() {
        return matchedAt;
    }
}
