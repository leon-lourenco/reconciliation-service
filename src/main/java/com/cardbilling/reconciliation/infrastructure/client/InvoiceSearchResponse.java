package com.cardbilling.reconciliation.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * One element of billing-service's {@code GET /invoices/search} response, bound to that service's
 * actual {@code InvoiceResponse} rather than to a pre-billing-service assumption about it.
 *
 * <p>The endpoint answers with a bare JSON array, not an {@code {"invoices": [...]}} wrapper - see
 * {@code InvoiceController.search} on billing-service. No results is {@code 200} with an empty
 * array, not {@code 404}: a search that found nothing is an answer, not an error.
 *
 * <p>{@code amountCents} is what the invoice <em>owes</em>: its own total plus any interest already
 * applied, which is the figure the legacy compared against ({@code totalAmountCents +
 * interestAppliedCents}). Matching against the total alone would silently stop matching any invoice
 * that had accrued interest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvoiceSearchResponse(Long id, String documentNumber, Long amountOwedCents, LocalDate dueDate,
        String status) {
}
