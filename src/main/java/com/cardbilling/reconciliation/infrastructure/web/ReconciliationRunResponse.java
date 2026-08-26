package com.cardbilling.reconciliation.infrastructure.web;

import com.cardbilling.reconciliation.domain.ReconciliationRun;
import java.time.LocalDateTime;

/**
 * @param unmatchedCount lines with no invoice for that customer in the date window (NOT_FOUND)
 * @param divergentCount lines whose customer has an invoice in the window owing a different
 *                       amount - a discrepancy someone should look at, not a miss
 */
public record ReconciliationRunResponse(Long id, String status, LocalDateTime startedAt, LocalDateTime finishedAt,
        int totalLines, int matchedCount, int unmatchedCount, int divergentCount) {

    static ReconciliationRunResponse from(ReconciliationRun run) {
        return new ReconciliationRunResponse(run.getId(), run.getStatus().name(), run.getStartedAt(),
                run.getFinishedAt(), run.getTotalLines(), run.getMatchedCount(), run.getUnmatchedCount(),
                run.getDivergentCount());
    }
}
