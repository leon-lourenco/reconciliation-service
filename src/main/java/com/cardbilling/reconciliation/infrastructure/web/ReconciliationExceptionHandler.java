package com.cardbilling.reconciliation.infrastructure.web;

import com.cardbilling.reconciliation.application.port.BillingServiceUnavailableException;
import com.cardbilling.reconciliation.domain.MalformedStatementLineException;
import com.cardbilling.reconciliation.domain.ReconciliationRunAlreadyInProgressException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

/**
 * Every failure this service can produce comes back as {@code application/problem+json} (RFC 7807,
 * Spring's own {@link ProblemDetail} - no extra library). No generic 500 with a stack trace in it.
 */
@RestControllerAdvice
public class ReconciliationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationExceptionHandler.class);
    private static final String PROBLEM_BASE = "urn:cardbilling:reconciliation:";

    /** A row that is not a statement line: the file reached us, but it is not one we can read. */
    @ExceptionHandler(MalformedStatementLineException.class)
    ProblemDetail handleMalformedStatementLine(MalformedStatementLineException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "malformed-statement-line",
                "Malformed statement line", e.getMessage());
    }

    /** Two runs at once would race for the same unmatched lines, so the second is refused. */
    @ExceptionHandler(ReconciliationRunAlreadyInProgressException.class)
    ProblemDetail handleRunAlreadyInProgress(ReconciliationRunAlreadyInProgressException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "reconciliation-run-in-progress",
                "Reconciliation already running", e.getMessage());
        problem.setProperty("runInProgressId", e.getRunInProgressId());
        return problem;
    }

    /** This service is fine; the one it depends on is not. */
    @ExceptionHandler(BillingServiceUnavailableException.class)
    ProblemDetail handleBillingServiceUnavailable(BillingServiceUnavailableException e) {
        log.warn("billing-service call failed: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "billing-service-unavailable",
                "billing-service unavailable", e.getMessage());
    }

    @ExceptionHandler({MultipartException.class, UncheckedIOException.class})
    ProblemDetail handleUnreadableUpload(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "unreadable-statement-file",
                "Statement file could not be read", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
