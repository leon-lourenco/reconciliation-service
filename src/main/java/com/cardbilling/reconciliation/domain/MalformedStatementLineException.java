package com.cardbilling.reconciliation.domain;

/** A row of an ingested statement file that this context cannot read as a statement line. */
public class MalformedStatementLineException extends RuntimeException {

    public MalformedStatementLineException(String message) {
        super(message);
    }
}
