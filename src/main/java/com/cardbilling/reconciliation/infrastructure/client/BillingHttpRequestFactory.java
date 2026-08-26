package com.cardbilling.reconciliation.infrastructure.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * The HTTP plumbing under the billing-service client, in one place so tests exercise the same
 * settings production runs with.
 *
 * <p>Two settings worth stating explicitly rather than inheriting:
 *
 * <ul>
 *   <li><strong>HTTP/1.1.</strong> The JDK client defaults to HTTP/2, which over plaintext means
 *       trying an h2c upgrade on every request. Not every server handles that on a request that
 *       carries a body - recording a payment is a POST - and the failure mode is an aborted
 *       connection rather than a clean error, which is a miserable thing to debug.
 *   <li><strong>Timeouts.</strong> A reconciliation run makes one call per statement line; without
 *       a read timeout a single unanswered call stalls the whole run indefinitely, and the circuit
 *       breaker never gets a failure to count.
 * </ul>
 */
public final class BillingHttpRequestFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private BillingHttpRequestFactory() {
    }

    public static ClientHttpRequestFactory create() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
