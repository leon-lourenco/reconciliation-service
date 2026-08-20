package com.cardbilling.reconciliation.domain;

import java.time.LocalDateTime;

/** One execution of reconciliation - the audit trail of "when did we run this, and what happened". */
public final class ReconciliationRun {

    public enum Status {
        RUNNING, COMPLETED, FAILED
    }

    private final Long id;
    private final LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Status status;
    private int totalLines;
    private int matchedCount;
    private int unmatchedCount;
    private int divergentCount;

    private ReconciliationRun(Long id, LocalDateTime startedAt, LocalDateTime finishedAt, Status status,
            int totalLines, int matchedCount, int unmatchedCount, int divergentCount) {
        this.id = id;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.totalLines = totalLines;
        this.matchedCount = matchedCount;
        this.unmatchedCount = unmatchedCount;
        this.divergentCount = divergentCount;
    }

    public static ReconciliationRun started() {
        return new ReconciliationRun(null, LocalDateTime.now(), null, Status.RUNNING, 0, 0, 0, 0);
    }

    /** Rebuilds a run already stored by this service - used by the persistence adapter only. */
    public static ReconciliationRun rehydrate(Long id, LocalDateTime startedAt, LocalDateTime finishedAt,
            Status status, int totalLines, int matchedCount, int unmatchedCount, int divergentCount) {
        return new ReconciliationRun(id, startedAt, finishedAt, status, totalLines, matchedCount,
                unmatchedCount, divergentCount);
    }

    public ReconciliationRun withId(Long assignedId) {
        return new ReconciliationRun(assignedId, startedAt, finishedAt, status, totalLines, matchedCount,
                unmatchedCount, divergentCount);
    }

    /** Argument order matches the legacy job's own {@code complete(total, matched, unmatched, divergent)}. */
    public void complete(int totalLines, int matchedCount, int unmatchedCount, int divergentCount) {
        this.totalLines = totalLines;
        this.matchedCount = matchedCount;
        this.unmatchedCount = unmatchedCount;
        this.divergentCount = divergentCount;
        this.finishedAt = LocalDateTime.now();
        this.status = Status.COMPLETED;
    }

    public void fail() {
        this.finishedAt = LocalDateTime.now();
        this.status = Status.FAILED;
    }

    public boolean isInProgress() {
        return status == Status.RUNNING;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Status getStatus() {
        return status;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getUnmatchedCount() {
        return unmatchedCount;
    }

    public int getDivergentCount() {
        return divergentCount;
    }
}
