package com.cardbilling.reconciliation.application.port;

/**
 * billing-service could not answer - it is down, timing out, the circuit breaker is open, or it
 * returned something this service cannot act on.
 *
 * <p>Part of the port's contract rather than of whichever HTTP client implements it, so a caller
 * can react to it without importing anything out of {@code infrastructure}.
 */
public class BillingServiceUnavailableException extends RuntimeException {

    public BillingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public BillingServiceUnavailableException(String message) {
        super(message);
    }
}
