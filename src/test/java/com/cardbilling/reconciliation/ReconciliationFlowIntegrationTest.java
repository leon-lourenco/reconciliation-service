package com.cardbilling.reconciliation;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardbilling.reconciliation.application.port.ReconciliationMatchRepositoryPort;
import com.cardbilling.reconciliation.application.port.StatementLineRepositoryPort;
import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import com.cardbilling.reconciliation.infrastructure.client.BillingServiceStubs;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole flow, against a real Postgres and a stubbed billing-service: upload a statement file,
 * run reconciliation, and check what ended up in this service's own tables and what it asked
 * billing-service along the way.
 *
 * <p>billing-service does not exist yet, so it is WireMock answering the contract written down in
 * {@link BillingServiceStubs} - including the Keycloak token endpoint, so the client-credentials
 * grant that guards every outbound call is exercised rather than switched off.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReconciliationFlowIntegrationTest.StubbedJwtDecoder.class)
class ReconciliationFlowIntegrationTest {

    private static final String REALM = "card-billing";
    private static final String HEADER = "external_reference,document_number,amount_cents,statement_date\n";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            // Testcontainers' 60s default is not enough for this image's first-run initdb on a
            // cold Docker Desktop, which is a flaky test rather than a real failure.
            .withStartupTimeout(Duration.ofMinutes(4));

    /** Stands in for billing-service and, on its own paths, for Keycloak's token endpoint. */
    @RegisterExtension
    static final WireMockExtension billing = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void pointAtTheContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("billing.base-url", billing::baseUrl);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> billing.baseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StatementLineRepositoryPort statementLines;

    @Autowired
    private ReconciliationMatchRepositoryPort matches;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("truncate table reconciliation_matches, reconciliation_runs, "
                + "external_statement_lines restart identity cascade");
        BillingServiceStubs.stubTokenEndpoint(billing, REALM);
    }

    private static MockMultipartFile statementFile(String csv) {
        return new MockMultipartFile("file", "statement.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private static String csvRow(String reference, String document, long amountCents, String date) {
        return "%s,%s,%d,%s%n".formatted(reference, document, amountCents, date);
    }

    @Test
    @DisplayName("ingest then match: one indexed lookup per line, a payment for the one that matches")
    void ingestsAStatementAndReconcilesEveryLine() throws Exception {
        // One line matching an invoice exactly, one whose customer owes a different amount, and one
        // whose customer billing-service knows nothing about.
        String matchingCustomer = "52998224725";
        String divergentCustomer = "11144477735";
        String unknownCustomer = "22233344455";

        BillingServiceStubs.stubExactMatch(billing, matchingCustomer, 125_000L, BillingServiceStubs.STATEMENT_DATE,
                BillingServiceStubs.INVOICE_ID, BillingServiceStubs.DUE_DATE);
        BillingServiceStubs.stubNoExactMatch(billing, divergentCustomer);
        BillingServiceStubs.stubNoExactMatch(billing, unknownCustomer);
        BillingServiceStubs.stubDocumentOnlySearch(billing, divergentCustomer,
                BillingServiceStubs.invoiceJson(77L, divergentCustomer, 98_500L, BillingServiceStubs.DUE_DATE));
        BillingServiceStubs.stubDocumentOnlySearch(billing, unknownCustomer);
        BillingServiceStubs.stubPaymentAccepted(billing);

        String csv = HEADER
                + csvRow("EXT-000001", matchingCustomer, 125_000L, "2026-03-10")
                + csvRow("EXT-000002", divergentCustomer, 98_000L, "2026-03-10")
                + csvRow("EXT-000003", unknownCustomer, 45_000L, "2026-03-10");

        mockMvc.perform(multipart("/statements/ingest").file(statementFile(csv)).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsRead").value(3))
                .andExpect(jsonPath("$.ingested").value(3))
                .andExpect(jsonPath("$.skippedDuplicates").value(0));

        mockMvc.perform(post("/statements/match").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalLines").value(3))
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.divergentCount").value(1))
                .andExpect(jsonPath("$.unmatchedCount").value(1));

        // The payment carries the statement line's own reference - billing-service's idempotency key.
        billing.verify(postRequestedFor(urlPathEqualTo("/invoices/42/payments"))
                .withRequestBody(matchingJsonPath("$.externalReference", equalTo("EXT-000001")))
                .withRequestBody(matchingJsonPath("$.source", equalTo("EXTERNAL_RECONCILIATION"))));

        // Three lines, three amount-filtered lookups - not one per invoice per line.
        billing.verify(3, getRequestedFor(urlPathEqualTo("/invoices/search"))
                .withQueryParam("amountCents", matching(".+")));
        // Two of them found nothing, so two fell back to the document-only lookup.
        billing.verify(2, getRequestedFor(urlPathEqualTo("/invoices/search"))
                .withQueryParam("amountCents", absent()));

        assertThat(statementLines.countUnmatched()).isEqualTo(2);
        assertThat(matches.findByRunId(1L))
                .extracting(ReconciliationMatch::getResult)
                .containsExactly(ReconciliationMatch.Result.MATCHED,
                        ReconciliationMatch.Result.DIVERGENT_AMOUNT,
                        ReconciliationMatch.Result.NOT_FOUND);
        assertThat(matches.findByRunId(1L).getFirst().getInvoiceId()).isEqualTo(BillingServiceStubs.INVOICE_ID);
    }

    @Test
    @DisplayName("a second run re-reconciles only what is still unmatched")
    void aSecondRunLeavesSettledLinesAlone() throws Exception {
        BillingServiceStubs.stubExactMatch(billing, BillingServiceStubs.DOCUMENT, 125_000L,
                BillingServiceStubs.STATEMENT_DATE, BillingServiceStubs.INVOICE_ID, BillingServiceStubs.DUE_DATE);
        BillingServiceStubs.stubPaymentAccepted(billing);

        mockMvc.perform(multipart("/statements/ingest")
                        .file(statementFile(HEADER
                                + csvRow("EXT-300001", BillingServiceStubs.DOCUMENT, 125_000L, "2026-03-10")))
                        .with(jwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/statements/match").with(jwt()))
                .andExpect(jsonPath("$.matchedCount").value(1));

        mockMvc.perform(post("/statements/match").with(jwt()))
                .andExpect(jsonPath("$.totalLines").value(0))
                .andExpect(jsonPath("$.matchedCount").value(0));

        // One payment, not two - the line was settled and is not looked at again.
        billing.verify(1, postRequestedFor(urlPathEqualTo("/invoices/42/payments")));
    }

    @Test
    @DisplayName("re-uploading the same file skips what is already stored instead of failing")
    void reUploadingAStatementIsANoOp() throws Exception {
        String csv = HEADER + csvRow("EXT-100001", BillingServiceStubs.DOCUMENT, 125_000L, "2026-03-10");

        mockMvc.perform(multipart("/statements/ingest").file(statementFile(csv)).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(1));

        mockMvc.perform(multipart("/statements/ingest").file(statementFile(csv)).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(0))
                .andExpect(jsonPath("$.skippedDuplicates").value(1));
    }

    @Test
    @DisplayName("a malformed row comes back as problem+json, not a stack trace")
    void rejectsAMalformedRowAsAProblemDetail() throws Exception {
        String csv = HEADER + "EXT-200001,52998224725,not-a-number,2026-03-10\n";

        mockMvc.perform(multipart("/statements/ingest").file(statementFile(csv)).with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:cardbilling:reconciliation:malformed-statement-line"))
                .andExpect(jsonPath("$.detail").value(containsString("amount_cents")));
    }

    @Test
    @DisplayName("billing-service being down fails the run loudly instead of completing it quietly")
    void reportsBillingServiceBeingDownAsProblemJson() throws Exception {
        billing.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/invoices/search"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(500)));

        mockMvc.perform(multipart("/statements/ingest")
                        .file(statementFile(HEADER
                                + csvRow("EXT-400001", BillingServiceStubs.DOCUMENT, 125_000L, "2026-03-10")))
                        .with(jwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/statements/match").with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:cardbilling:reconciliation:billing-service-unavailable"));

        // The run is FAILED, not RUNNING, so the next attempt is not refused as "already in progress".
        assertThat(jdbcTemplate.queryForObject("select status from reconciliation_runs order by id desc limit 1",
                String.class)).isEqualTo("FAILED");
        assertThat(statementLines.countUnmatched()).isEqualTo(1);
    }

    @Test
    void refusesEveryEndpointWithoutAToken() throws Exception {
        mockMvc.perform(post("/statements/match")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/statements/ingest").file(statementFile(HEADER)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Replaces the real decoder so the resource-server half of security is exercised without a
     * Keycloak to fetch a JWK set from. The outbound half is not stubbed out - that really does go
     * through the client-credentials grant against the token endpoint above.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubbedJwtDecoder {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("reconciliation-service")
                    .claim("scope", "reconciliation")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .build();
        }
    }
}
