package com.cardbilling.reconciliation.infrastructure.client;

import java.time.LocalDateTime;

/**
 * The body of {@code POST /invoices/{id}/payments} on billing-service, per ARCHITECTURE.md:
 * {@code {amountCents, source, externalReference, paidAt}}.
 *
 * <p>{@code externalReference} is billing-service's idempotency key for a payment, so this is also
 * what makes replaying a reconciliation run safe.
 */
public record RecordPaymentRequest(long amountCents, String source, String externalReference, LocalDateTime paidAt) {

    /** The only source this service ever records - it exists to reconcile external statements. */
    public static final String EXTERNAL_RECONCILIATION = "EXTERNAL_RECONCILIATION";

    public static RecordPaymentRequest externalReconciliation(long amountCents, String externalReference,
            LocalDateTime paidAt) {
        return new RecordPaymentRequest(amountCents, EXTERNAL_RECONCILIATION, externalReference, paidAt);
    }
}
