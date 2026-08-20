package com.cardbilling.reconciliation.domain;

/**
 * A second reconciliation run was asked for while one is still RUNNING. Two concurrent runs would
 * race each other to match the same unmatched lines and could record the same payment twice under
 * two different runs, so the second request is refused rather than queued.
 */
public class ReconciliationRunAlreadyInProgressException extends RuntimeException {

    private final Long runInProgressId;

    public ReconciliationRunAlreadyInProgressException(Long runInProgressId) {
        super("Reconciliation run " + runInProgressId + " is still in progress");
        this.runInProgressId = runInProgressId;
    }

    public Long getRunInProgressId() {
        return runInProgressId;
    }
}
