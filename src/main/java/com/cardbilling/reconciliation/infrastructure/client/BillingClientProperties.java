package com.cardbilling.reconciliation.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl              where billing-service answers
 * @param clientRegistrationId which OAuth2 client registration to get a token from before calling it
 */
@ConfigurationProperties(prefix = "billing")
public record BillingClientProperties(String baseUrl, String clientRegistrationId) {

    public BillingClientProperties {
        if (clientRegistrationId == null || clientRegistrationId.isBlank()) {
            clientRegistrationId = "billing-service";
        }
    }
}
