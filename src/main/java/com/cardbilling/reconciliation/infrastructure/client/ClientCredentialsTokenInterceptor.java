package com.cardbilling.reconciliation.infrastructure.client;

import com.cardbilling.reconciliation.application.port.BillingServiceUnavailableException;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Puts this service's own access token on every outbound call to billing-service.
 *
 * <p>No endpoint in this platform is reachable without a token, including between services, so the
 * client half of OAuth2 is not optional here. The token is obtained by the client-credentials
 * grant against Keycloak and cached by the authorized-client manager until it expires; there is no
 * user and no request context involved, which is why this uses the manager directly rather than
 * anything that reads from a servlet request.
 */
public class ClientCredentialsTokenInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String clientRegistrationId;

    public ClientCredentialsTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager,
            String clientRegistrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistrationId = clientRegistrationId;
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
            @NonNull ClientHttpRequestExecution execution) throws IOException {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(clientRegistrationId)
                .build();

        OAuth2AuthorizedClient authorizedClient;
        try {
            authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        } catch (RuntimeException e) {
            throw new BillingServiceUnavailableException(
                    "Could not obtain an access token for '" + clientRegistrationId + "'", e);
        }
        if (authorizedClient == null) {
            throw new BillingServiceUnavailableException(
                    "No access token issued for '" + clientRegistrationId + "'");
        }

        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
