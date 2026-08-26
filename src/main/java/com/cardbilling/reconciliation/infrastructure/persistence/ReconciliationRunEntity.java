package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.domain.ReconciliationRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** How {@link ReconciliationRun} is stored. */
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconciliationRun.Status status;

    @Column(name = "total_lines", nullable = false)
    private int totalLines;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "unmatched_count", nullable = false)
    private int unmatchedCount;

    @Column(name = "divergent_count", nullable = false)
    private int divergentCount;

    protected ReconciliationRunEntity() {
    }

    static ReconciliationRunEntity fromDomain(ReconciliationRun run) {
        ReconciliationRunEntity entity = new ReconciliationRunEntity();
        entity.id = run.getId();
        entity.startedAt = run.getStartedAt();
        entity.finishedAt = run.getFinishedAt();
        entity.status = run.getStatus();
        entity.totalLines = run.getTotalLines();
        entity.matchedCount = run.getMatchedCount();
        entity.unmatchedCount = run.getUnmatchedCount();
        entity.divergentCount = run.getDivergentCount();
        return entity;
    }

    ReconciliationRun toDomain() {
        return ReconciliationRun.rehydrate(id, startedAt, finishedAt, status, totalLines, matchedCount,
                unmatchedCount, divergentCount);
    }
}
