package com.cardbilling.reconciliation.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * What {@code GET /invoices/search} on billing-service comes back with.
 *
 * <p><strong>Assumed contract.</strong> ARCHITECTURE.md pins that endpoint's query parameters
 * ({@code documentNumber}, {@code amountCents}, {@code aroundDate}, {@code toleranceDays}) but not
 * its response body, and billing-service does not exist yet. This is the shape assumed here, and
 * the shape the WireMock stubs in this service's tests are written against:
 *
 * <pre>
 * {"invoices": [
 *    {"id": 42, "documentNumber": "52998224725", "amountCents": 125000,
 *     "dueDate": "2026-03-08", "status": "OVERDUE"}
 * ]}
 * </pre>
 *
 * <p>Three assumptions worth reconciling against billing-service when it is built:
 * <ul>
 *   <li>An object with an {@code invoices} array, not a bare array, so the endpoint can grow a
 *       page marker later without breaking callers.
 *   <li>No results is {@code 200} with an empty array, not {@code 404} - a search that found
 *       nothing is an answer, not an error.
 *   <li>{@code amountCents} is what the invoice <em>owes</em>: its own total plus any interest
 *       already applied, which is the figure the legacy compared against
 *       ({@code totalAmountCents + interestAppliedCents}). Matching against the total alone would
 *       silently stop matching any invoice that had accrued interest.
 * </ul>
 * Unknown fields are ignored, so billing-service returning more than this does not break anything.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvoiceSearchResponse(List<Invoice> invoices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Invoice(Long id, String documentNumber, Long amountCents, LocalDate dueDate, String status) {
    }

    public List<Invoice> invoicesOrEmpty() {
        return invoices == null ? List.of() : invoices;
    }
}
