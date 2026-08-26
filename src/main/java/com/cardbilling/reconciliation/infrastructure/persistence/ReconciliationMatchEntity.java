package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * How {@link ReconciliationMatch} is stored.
 *
 * <p>{@code invoice_id} is billing-service's identifier, stored as a plain value: there is no
 * foreign key because the invoice is not in this database and never will be. The legacy could map
 * a {@code @ManyToOne} to it only because everything shared one schema.
 */
@Entity
@Table(name = "reconciliation_matches")
public class ReconciliationMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "statement_line_id", nullable = false)
    private Long statementLineId;

    /** Null unless the result is MATCHED. Owned by billing-service. */
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 32)
    private ReconciliationMatch.Result result;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

    protected ReconciliationMatchEntity() {
    }

    static ReconciliationMatchEntity fromDomain(ReconciliationMatch match) {
        ReconciliationMatchEntity entity = new ReconciliationMatchEntity();
        entity.id = match.getId();
        entity.runId = match.getRunId();
        entity.statementLineId = match.getStatementLineId();
        entity.invoiceId = match.getInvoiceId();
        entity.result = match.getResult();
        entity.matchedAt = match.getMatchedAt();
        return entity;
    }

    ReconciliationMatch toDomain() {
        return ReconciliationMatch.rehydrate(id, runId, statementLineId, invoiceId, result, matchedAt);
    }
}
