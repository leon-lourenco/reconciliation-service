package com.cardbilling.reconciliation.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDate;

/**
 * The one place billing-service's real {@code GET /invoices/search} contract is written down for
 * this service's tests - see {@link InvoiceSearchResponse}. If billing-service's response shape
 * changes, this file is what has to change.
 */
public final class BillingServiceStubs {

    public static final String DOCUMENT = "52998224725";
    public static final long AMOUNT_CENTS = 125_000L;
    public static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 3, 10);
    public static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 8);
    public static final long INVOICE_ID = 42L;

    private BillingServiceStubs() {
    }

    public static String invoiceJson(long invoiceId, String documentNumber, long owedCents, LocalDate dueDate) {
        return """
                {"id": %d, "documentNumber": "%s", "amountOwedCents": %d, "dueDate": "%s", "status": "OVERDUE"}"""
                .formatted(invoiceId, documentNumber, owedCents, dueDate);
    }

    public static String searchResponse(String... invoices) {
        return "[" + String.join(",", invoices) + "]";
    }

    /** The amount-filtered lookup finds an invoice owing exactly what the statement line says. */
    public static void stubExactMatch(WireMockExtension billing, String documentNumber, long amountCents,
            LocalDate aroundDate, long invoiceId, LocalDate dueDate) {
        billing.stubFor(get(urlPathEqualTo("/invoices/search"))
                .withQueryParam("documentNumber", equalTo(documentNumber))
                .withQueryParam("amountCents", equalTo(String.valueOf(amountCents)))
                .withQueryParam("aroundDate", equalTo(aroundDate.toString()))
                .withQueryParam("toleranceDays", equalTo("3"))
                .willReturn(okJson(searchResponse(
                        invoiceJson(invoiceId, documentNumber, amountCents, dueDate)))));
    }

    /** The amount-filtered lookup finds nothing - a search with no results is 200 and an empty array. */
    public static void stubNoExactMatch(WireMockExtension billing, String documentNumber) {
        billing.stubFor(get(urlPathEqualTo("/invoices/search"))
                .withQueryParam("documentNumber", equalTo(documentNumber))
                .withQueryParam("amountCents", matching(".+"))
                .willReturn(okJson(searchResponse())));
    }

    /**
     * The fallback lookup - same customer and window, no amount filter. What comes back here is
     * what separates DIVERGENT_AMOUNT from NOT_FOUND.
     */
    public static void stubDocumentOnlySearch(WireMockExtension billing, String documentNumber, String... invoices) {
        billing.stubFor(get(urlPathEqualTo("/invoices/search"))
                .withQueryParam("documentNumber", equalTo(documentNumber))
                .withQueryParam("amountCents", absent())
                .withQueryParam("toleranceDays", equalTo("3"))
                .willReturn(okJson(searchResponse(invoices))));
    }

    public static void stubPaymentAccepted(WireMockExtension billing) {
        billing.stubFor(post(urlPathMatching("/invoices/[0-9]+/payments"))
                .willReturn(aResponse().withStatus(201)));
    }

    /** billing-service's idempotency on externalReference, seen from this side: a replay conflicts. */
    public static void stubPaymentAlreadyRecorded(WireMockExtension billing) {
        billing.stubFor(post(urlPathMatching("/invoices/[0-9]+/payments"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {"type": "urn:cardbilling:billing:duplicate-payment", \
                                "title": "Duplicate payment", "status": 409}""")));
    }

    /** The Keycloak token endpoint, so the client-credentials grant has something to answer it. */
    public static void stubTokenEndpoint(WireMockExtension keycloak, String realm) {
        keycloak.stubFor(post(urlPathEqualTo("/realms/" + realm + "/protocol/openid-connect/token"))
                .willReturn(okJson("""
                        {"access_token": "test-access-token", "token_type": "Bearer", "expires_in": 900}""")));
    }
}
