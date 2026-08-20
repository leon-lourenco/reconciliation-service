package com.cardbilling.reconciliation.domain;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * When an external statement line and an invoice are the same payment.
 *
 * <p>Carried over unchanged from the legacy monolith, because the rule was never the problem - the
 * way it was evaluated was. An external bank statement and this system's invoices share no
 * identifier, so a match is decided on customer document number, amount, and a statement date
 * within {@value #DATE_TOLERANCE_DAYS} days of the invoice's due date.
 *
 * <p>The legacy evaluated that rule by loading every open invoice and scanning - O(lines ×
 * invoices). Here billing-service evaluates the document-and-date half of it against a real index
 * and returns candidates; this class stays as the single written-down statement of the rule, and
 * re-checks it against whatever came back rather than trusting the query to have applied it.
 */
public final class StatementMatchRule {

    public static final int DATE_TOLERANCE_DAYS = 3;

    private StatementMatchRule() {
    }

    /** Same customer, and a due date close enough to the statement date to be the same payment. */
    public static boolean isCandidateFor(InvoiceCandidate invoice, ExternalStatementLine line) {
        if (!invoice.documentNumber().equals(line.getCustomerDocumentNumber())) {
            return false;
        }
        long daysApart = Math.abs(ChronoUnit.DAYS.between(invoice.dueDate(), line.getStatementDate()));
        return daysApart <= DATE_TOLERANCE_DAYS;
    }

    /** A candidate that also owes exactly what the statement says was paid. */
    public static boolean isExactMatchFor(InvoiceCandidate invoice, ExternalStatementLine line) {
        return isCandidateFor(invoice, line) && invoice.owedAmountCents() == line.getAmountCents();
    }

    /**
     * The first invoice that matches on all three of document, date window and amount. The legacy
     * took the first one it hit too ({@code break} out of the inner loop), so a statement line
     * still settles exactly one invoice.
     */
    public static Optional<InvoiceCandidate> firstExactMatch(List<InvoiceCandidate> invoices,
            ExternalStatementLine line) {
        return invoices.stream().filter(invoice -> isExactMatchFor(invoice, line)).findFirst();
    }

    /**
     * True when this customer has an invoice inside the date window but none of them owes the
     * statement's amount - the legacy's {@code sawDivergentAmount}, which is what separates
     * "we found the wrong number" from "we found nothing at all".
     */
    public static boolean hasDivergentAmount(List<InvoiceCandidate> invoices, ExternalStatementLine line) {
        return invoices.stream().anyMatch(invoice -> isCandidateFor(invoice, line)
                && invoice.owedAmountCents() != line.getAmountCents());
    }

    /** Classifies a statement line against every invoice the search turned up for its customer. */
    public static ReconciliationMatch.Result classify(List<InvoiceCandidate> invoices, ExternalStatementLine line) {
        if (firstExactMatch(invoices, line).isPresent()) {
            return ReconciliationMatch.Result.MATCHED;
        }
        return hasDivergentAmount(invoices, line)
                ? ReconciliationMatch.Result.DIVERGENT_AMOUNT
                : ReconciliationMatch.Result.NOT_FOUND;
    }
}
