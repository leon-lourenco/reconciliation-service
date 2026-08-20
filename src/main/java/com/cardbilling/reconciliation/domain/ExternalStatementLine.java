package com.cardbilling.reconciliation.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One row from an ingested external statement, before reconciliation attempts a match.
 *
 * <p>Same shape as the legacy monolith's entity of the same name, minus the JPA annotations: the
 * persistence mapping lives in {@code infrastructure.persistence} so this type stays plain Java
 * and can be exercised without a Spring context or a database.
 */
public final class ExternalStatementLine {

    private final Long id;
    private final String externalReference;
    private final String customerDocumentNumber;
    private final long amountCents;
    private final LocalDate statementDate;
    private final String rawLine;
    private final LocalDateTime ingestedAt;
    private boolean matched;

    private ExternalStatementLine(Long id, String externalReference, String customerDocumentNumber,
            long amountCents, LocalDate statementDate, String rawLine, LocalDateTime ingestedAt, boolean matched) {
        this.id = id;
        this.externalReference = externalReference;
        this.customerDocumentNumber = customerDocumentNumber;
        this.amountCents = amountCents;
        this.statementDate = statementDate;
        this.rawLine = rawLine;
        this.ingestedAt = ingestedAt;
        this.matched = matched;
    }

    /** A newly ingested line: no database identity yet, not matched yet. */
    public static ExternalStatementLine ingested(String externalReference, String customerDocumentNumber,
            long amountCents, LocalDate statementDate, String rawLine) {
        requireText(externalReference, "external_reference");
        requireText(customerDocumentNumber, "document_number");
        if (amountCents <= 0) {
            throw new MalformedStatementLineException("amount_cents must be positive, was " + amountCents);
        }
        Objects.requireNonNull(statementDate, "statementDate");
        return new ExternalStatementLine(null, externalReference, customerDocumentNumber, amountCents,
                statementDate, rawLine, LocalDateTime.now(), false);
    }

    /** Rebuilds a line already stored by this service - used by the persistence adapter only. */
    public static ExternalStatementLine rehydrate(Long id, String externalReference, String customerDocumentNumber,
            long amountCents, LocalDate statementDate, String rawLine, LocalDateTime ingestedAt, boolean matched) {
        return new ExternalStatementLine(id, externalReference, customerDocumentNumber, amountCents,
                statementDate, rawLine, ingestedAt, matched);
    }

    /** Same identity the line will carry once persisted, for a line that has been saved. */
    public ExternalStatementLine withId(Long assignedId) {
        return new ExternalStatementLine(assignedId, externalReference, customerDocumentNumber, amountCents,
                statementDate, rawLine, ingestedAt, matched);
    }

    public void markMatched() {
        this.matched = true;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MalformedStatementLineException(field + " must not be blank");
        }
    }

    public Long getId() {
        return id;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getCustomerDocumentNumber() {
        return customerDocumentNumber;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public String getRawLine() {
        return rawLine;
    }

    public LocalDateTime getIngestedAt() {
        return ingestedAt;
    }

    public boolean isMatched() {
        return matched;
    }

    @Override
    public String toString() {
        return "ExternalStatementLine[" + externalReference + ", " + customerDocumentNumber + ", "
                + amountCents + ", " + statementDate + "]";
    }
}
