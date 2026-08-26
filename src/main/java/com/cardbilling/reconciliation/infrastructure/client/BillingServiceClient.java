package com.cardbilling.reconciliation.infrastructure.client;

import com.cardbilling.reconciliation.application.port.BillingServicePort;
import com.cardbilling.reconciliation.application.port.BillingServiceUnavailableException;
import com.cardbilling.reconciliation.domain.InvoiceCandidate;
import com.cardbilling.reconciliation.domain.StatementMatchRule;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls billing-service, which owns invoices and payments.
 *
 * <p>Both searches hit {@code GET /invoices/search}, the indexed lookup that replaces the legacy's
 * nested loop. Every call is wrapped in a circuit breaker and a retry (see
 * {@code resilience4j} in application.yml): billing-service being slow or down is a normal
 * operating condition, and a reconciliation run should fail loudly and re-runnably rather than
 * hang on it.
 */
public class BillingServiceClient implements BillingServicePort {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceClient.class);
    private static final String INSTANCE = "billingService";
    private static final String SEARCH_PATH = "/invoices/search";

    private final RestClient restClient;

    public BillingServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public Optional<InvoiceCandidate> searchInvoice(String documentNumber, long amountCents, LocalDate aroundDate) {
        InvoiceSearchResponse response = search(documentNumber, amountCents, aroundDate);
        return response.invoicesOrEmpty().stream().findFirst().map(BillingServiceClient::toCandidate);
    }

    @Override
    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public List<InvoiceCandidate> searchInvoicesByDocument(String documentNumber, LocalDate aroundDate) {
        InvoiceSearchResponse response = search(documentNumber, null, aroundDate);
        return response.invoicesOrEmpty().stream().map(BillingServiceClient::toCandidate).toList();
    }

    @Override
    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public void recordPayment(long invoiceId, long amountCents, String externalReference, LocalDateTime paidAt) {
        RecordPaymentRequest request =
                RecordPaymentRequest.externalReconciliation(amountCents, externalReference, paidAt);
        try {
            restClient.post()
                    .uri("/invoices/{id}/payments", invoiceId)
                    .body(request)
                    .retrieve()
                    // billing-service makes externalReference unique, so a conflict means this exact
                    // statement line was already recorded against this invoice - a replayed run, not
                    // a failure. Treated as done, which is what makes re-running a run safe.
                    .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(), (req, res) ->
                            log.info("Payment for external reference {} was already recorded on invoice {}",
                                    externalReference, invoiceId))
                    .onStatus(status -> status.isError() && status.value() != HttpStatus.CONFLICT.value(),
                            (req, res) -> {
                                throw new BillingServiceUnavailableException("billing-service answered "
                                        + res.getStatusCode() + " recording a payment on invoice " + invoiceId);
                            })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BillingServiceUnavailableException(
                    "billing-service could not be reached to record a payment on invoice " + invoiceId, e);
        }
    }

    /** One call to the indexed search. {@code amountCents} is left off for the divergence check. */
    private InvoiceSearchResponse search(String documentNumber, Long amountCents, LocalDate aroundDate) {
        try {
            InvoiceSearchResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(SEARCH_PATH)
                                .queryParam("documentNumber", documentNumber)
                                .queryParam("aroundDate", aroundDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                                .queryParam("toleranceDays", StatementMatchRule.DATE_TOLERANCE_DAYS);
                        if (amountCents != null) {
                            uriBuilder.queryParam("amountCents", amountCents);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, (req, res) -> { })
                    .onStatus(status -> status.isError() && status.value() != HttpStatus.NOT_FOUND.value(),
                            (req, res) -> {
                                throw new BillingServiceUnavailableException(
                                        "billing-service answered " + res.getStatusCode() + " to an invoice search");
                            })
                    .body(InvoiceSearchResponse.class);
            return response == null ? new InvoiceSearchResponse(List.of()) : response;
        } catch (RestClientException e) {
            throw new BillingServiceUnavailableException("billing-service could not be reached for an invoice search", e);
        }
    }

    private static InvoiceCandidate toCandidate(InvoiceSearchResponse.Invoice invoice) {
        if (invoice.id() == null || invoice.amountCents() == null || invoice.dueDate() == null) {
            throw new BillingServiceUnavailableException(
                    "billing-service returned an invoice without an id, amount or due date: " + invoice);
        }
        return new InvoiceCandidate(invoice.id(), invoice.documentNumber(), invoice.amountCents(), invoice.dueDate());
    }
}
