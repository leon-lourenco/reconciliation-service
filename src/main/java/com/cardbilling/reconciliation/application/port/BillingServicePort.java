package com.cardbilling.reconciliation.application.port;

import com.cardbilling.reconciliation.domain.InvoiceCandidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Everything this service needs from billing-service, which owns invoices and payments.
 *
 * <p>The two search methods are the replacement for the legacy's nested loop. Both are indexed
 * lookups on billing-service's side ({@code GET /invoices/search}); neither returns a data dump
 * this service then scans.
 */
public interface BillingServicePort {

    /**
     * The invoice this statement line settles, if there is one: same customer document, same
     * amount owed, due date within {@code toleranceDays} of {@code aroundDate}. This is the call
     * the happy path makes, one per statement line.
     */
    Optional<InvoiceCandidate> searchInvoice(String documentNumber, long amountCents, LocalDate aroundDate);

    /**
     * The same lookup with the amount left off - every unpaid invoice for this customer inside the
     * date window, whatever it owes.
     *
     * <p>Only called when {@link #searchInvoice} came back empty, and only to tell two very
     * different outcomes apart: a statement line for a customer who has an invoice in the window
     * that owes a different amount (a discrepancy someone needs to look at) versus one with no
     * invoice at all (most likely not ours). The legacy got that distinction for free because it
     * had every invoice in memory already; here it costs one extra indexed lookup on the lines
     * that did not match, and still never loads the invoice table.
     */
    List<InvoiceCandidate> searchInvoicesByDocument(String documentNumber, LocalDate aroundDate);

    /**
     * Records the statement line against the invoice as a payment.
     *
     * <p>{@code externalReference} is the statement line's own reference, which billing-service
     * treats as the payment's idempotency key - replaying a reconciliation run records nothing
     * twice. The payment source is always external reconciliation; this service has no other.
     */
    void recordPayment(long invoiceId, long amountCents, String externalReference, LocalDateTime paidAt);
}
