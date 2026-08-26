package com.cardbilling.reconciliation.infrastructure.config;

import com.cardbilling.reconciliation.application.port.BillingServicePort;
import com.cardbilling.reconciliation.infrastructure.client.BillingClientProperties;
import com.cardbilling.reconciliation.infrastructure.client.BillingHttpRequestFactory;
import com.cardbilling.reconciliation.infrastructure.client.BillingServiceClient;
import com.cardbilling.reconciliation.infrastructure.client.ClientCredentialsTokenInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingClientProperties.class)
public class BillingClientConfiguration {

    /**
     * Client-credentials only: there is no user behind a reconciliation run, and no servlet request
     * to read an authentication from, so the service-level manager is the right one here.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientService authorizedClients) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient billingRestClient(RestClient.Builder builder, BillingClientProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager) {
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(BillingHttpRequestFactory.create())
                .requestInterceptor(new ClientCredentialsTokenInterceptor(
                        authorizedClientManager, properties.clientRegistrationId()))
                .build();
    }

    @Bean
    BillingServicePort billingServicePort(RestClient billingRestClient) {
        return new BillingServiceClient(billingRestClient);
    }
}
