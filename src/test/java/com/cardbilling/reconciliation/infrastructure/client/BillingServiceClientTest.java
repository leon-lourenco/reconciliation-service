package com.cardbilling.reconciliation.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.cardbilling.reconciliation.application.port.BillingServiceUnavailableException;
import com.cardbilling.reconciliation.domain.InvoiceCandidate;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

/**
 * Pins what this service sends to billing-service and what it does with the answer. The HTTP
 * plumbing is the same {@link BillingHttpRequestFactory} production uses. Resilience4j is
 * not in the way here - the annotations are exercised in the Spring context, this is the HTTP
 * contract on its own.
 */
class BillingServiceClientTest {

    @RegisterExtension
    static final WireMockExtension billing = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private BillingServiceClient client;

    @BeforeEach
    void setUp() {
        client = new BillingServiceClient(RestClient.builder()
                .baseUrl(billing.baseUrl())
                .requestFactory(BillingHttpRequestFactory.create())
                .build());
    }

    @Test
    @DisplayName("the amount-filtered search sends every parameter ARCHITECTURE.md pins, including toleranceDays=3")
    void searchesWithDocumentAmountDateAndTolerance() {
        BillingServiceStubs.stubExactMatch(billing, BillingServiceStubs.DOCUMENT, BillingServiceStubs.AMOUNT_CENTS,
                BillingServiceStubs.STATEMENT_DATE, BillingServiceStubs.INVOICE_ID, BillingServiceStubs.DUE_DATE);

        Optional<InvoiceCandidate> found = client.searchInvoice(BillingServiceStubs.DOCUMENT,
                BillingServiceStubs.AMOUNT_CENTS, BillingServiceStubs.STATEMENT_DATE);

        assertThat(found).contains(new InvoiceCandidate(BillingServiceStubs.INVOICE_ID,
                BillingServiceStubs.DOCUMENT, BillingServiceStubs.AMOUNT_CENTS, BillingServiceStubs.DUE_DATE));
        billing.verify(getRequestedFor(urlPathEqualTo("/invoices/search"))
                .withQueryParam("documentNumber", equalTo(BillingServiceStubs.DOCUMENT))
                .withQueryParam("amountCents", equalTo("125000"))
                .withQueryParam("aroundDate", equalTo("2026-03-10"))
                .withQueryParam("toleranceDays", equalTo("3")));
    }

    @Test
    void returnsNothingWhenTheSearchComesBackEmpty() {
        BillingServiceStubs.stubNoExactMatch(billing, BillingServiceStubs.DOCUMENT);

        assertThat(client.searchInvoice(BillingServiceStubs.DOCUMENT, BillingServiceStubs.AMOUNT_CENTS,
                BillingServiceStubs.STATEMENT_DATE)).isEmpty();
    }

    @Test
    @DisplayName("the divergence lookup is the same endpoint with the amount left off")
    void searchesByDocumentWithoutAnAmount() {
        BillingServiceStubs.stubDocumentOnlySearch(billing, BillingServiceStubs.DOCUMENT,
                BillingServiceStubs.invoiceJson(7L, BillingServiceStubs.DOCUMENT, 99_000L,
                        BillingServiceStubs.DUE_DATE));

        List<InvoiceCandidate> candidates = client.searchInvoicesByDocument(
                BillingServiceStubs.DOCUMENT, BillingServiceStubs.STATEMENT_DATE);

        assertThat(candidates).containsExactly(
                new InvoiceCandidate(7L, BillingServiceStubs.DOCUMENT, 99_000L, BillingServiceStubs.DUE_DATE));
        billing.verify(getRequestedFor(urlPathEqualTo("/invoices/search"))
                .withQueryParam("amountCents", absent()));
    }

    @Test
    @DisplayName("a payment carries the statement line's reference, which is what makes a replay a no-op")
    void postsThePaymentBodyBillingServiceDocuments() {
        BillingServiceStubs.stubPaymentAccepted(billing);

        client.recordPayment(BillingServiceStubs.INVOICE_ID, BillingServiceStubs.AMOUNT_CENTS, "EXT-000123",
                LocalDateTime.of(2026, 3, 10, 0, 0));

        billing.verify(postRequestedFor(urlPathEqualTo("/invoices/42/payments"))
                .withRequestBody(equalToJson("""
                        {"amountCents": 125000,
                         "source": "EXTERNAL_RECONCILIATION",
                         "externalReference": "EXT-000123",
                         "paidAt": "2026-03-10T00:00:00"}""")));
    }

    @Test
    @DisplayName("a 409 means this line was already paid in an earlier run - not a failure")
    void treatsADuplicatePaymentAsAlreadyDone() {
        BillingServiceStubs.stubPaymentAlreadyRecorded(billing);

        client.recordPayment(BillingServiceStubs.INVOICE_ID, BillingServiceStubs.AMOUNT_CENTS, "EXT-000123",
                LocalDateTime.of(2026, 3, 10, 0, 0));

        billing.verify(postRequestedFor(urlPathEqualTo("/invoices/42/payments")));
    }

    @Test
    void reportsBillingServiceFailingASearch() {
        billing.stubFor(get(urlPathEqualTo("/invoices/search")).willReturn(aResponse().withStatus(500)));

        assertThatExceptionOfType(BillingServiceUnavailableException.class)
                .isThrownBy(() -> client.searchInvoice(BillingServiceStubs.DOCUMENT,
                        BillingServiceStubs.AMOUNT_CENTS, BillingServiceStubs.STATEMENT_DATE))
                .withMessageContaining("500");
    }

    @Test
    void reportsBillingServiceRejectingAPayment() {
        billing.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/invoices/42/payments")).willReturn(aResponse().withStatus(503)));

        assertThatExceptionOfType(BillingServiceUnavailableException.class)
                .isThrownBy(() -> client.recordPayment(42L, 1L, "EXT-1", LocalDateTime.now()));
    }

    @Test
    @DisplayName("an invoice missing the fields the rule needs is a contract breach, not a silent miss")
    void refusesAnInvoiceWithoutAnAmountOrDueDate() {
        billing.stubFor(get(urlPathEqualTo("/invoices/search"))
                .willReturn(okJson("""
                        {"invoices": [{"id": 42, "documentNumber": "52998224725"}]}""")));

        assertThatExceptionOfType(BillingServiceUnavailableException.class)
                .isThrownBy(() -> client.searchInvoice(BillingServiceStubs.DOCUMENT,
                        BillingServiceStubs.AMOUNT_CENTS, BillingServiceStubs.STATEMENT_DATE));
    }
}
