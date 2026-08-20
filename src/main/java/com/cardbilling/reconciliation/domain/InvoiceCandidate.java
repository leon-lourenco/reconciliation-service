package com.cardbilling.reconciliation.domain;

import java.time.LocalDate;

/**
 * An invoice billing-service returned as a possible counterpart to a statement line, in this
 * context's own terms.
 *
 * <p>Deliberately not an invoice: this service does not own invoices and never sees the whole of
 * one. It sees the three fields the matching rule needs - whose invoice it is, what it still owes,
 * and when it was due.
 *
 * @param owedAmountCents what the invoice owes in total (its own amount plus any interest already
 *                        applied) - the same figure the legacy computed inline as
 *                        {@code totalAmountCents + interestAppliedCents}.
 */
public record InvoiceCandidate(long invoiceId, String documentNumber, long owedAmountCents, LocalDate dueDate) {
}
