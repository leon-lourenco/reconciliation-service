package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** How {@link ExternalStatementLine} is stored. The domain type carries no annotations of its own. */
@Entity
@Table(name = "external_statement_lines")
public class ExternalStatementLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_reference", nullable = false, unique = true, length = 128)
    private String externalReference;

    @Column(name = "customer_document_number", nullable = false, length = 32)
    private String customerDocumentNumber;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "raw_line", nullable = false, length = 1024)
    private String rawLine;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    protected ExternalStatementLineEntity() {
    }

    static ExternalStatementLineEntity fromDomain(ExternalStatementLine line) {
        ExternalStatementLineEntity entity = new ExternalStatementLineEntity();
        entity.id = line.getId();
        entity.externalReference = line.getExternalReference();
        entity.customerDocumentNumber = line.getCustomerDocumentNumber();
        entity.amountCents = line.getAmountCents();
        entity.statementDate = line.getStatementDate();
        entity.rawLine = line.getRawLine();
        entity.ingestedAt = line.getIngestedAt();
        entity.matched = line.isMatched();
        return entity;
    }

    ExternalStatementLine toDomain() {
        return ExternalStatementLine.rehydrate(id, externalReference, customerDocumentNumber, amountCents,
                statementDate, rawLine, ingestedAt, matched);
    }
}
