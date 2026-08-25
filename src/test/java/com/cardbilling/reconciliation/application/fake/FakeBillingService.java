package com.cardbilling.reconciliation.application.fake;

import com.cardbilling.reconciliation.application.port.BillingServicePort;
import com.cardbilling.reconciliation.domain.InvoiceCandidate;
import com.cardbilling.reconciliation.domain.StatementMatchRule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stands in for billing-service, behaving the way its indexed search is contracted to: it answers
 * about the invoices it holds and never hands the whole set over.
 *
 * <p>It also counts its calls, which is what lets a test assert the thing this service exists for -
 * that a run costs a fixed number of lookups per statement line, not one per invoice per line.
 */
public class FakeBillingService implements BillingServicePort {

    public record RecordedPayment(long invoiceId, long amountCents, String externalReference, LocalDateTime paidAt) {
    }

    private final List<InvoiceCandidate> invoices = new ArrayList<>();
    private final List<RecordedPayment> payments = new ArrayList<>();
    private int searchInvoiceCalls;
    private int searchByDocumentCalls;
    private RuntimeException failureToThrow;
    private int failAfterCalls = Integer.MAX_VALUE;

    public FakeBillingService withInvoice(InvoiceCandidate invoice) {
        invoices.add(invoice);
        return this;
    }

    /** Makes the next search after {@code calls} successful searches blow up, as an outage would. */
    public FakeBillingService failingAfter(int calls, RuntimeException failure) {
        this.failAfterCalls = calls;
        this.failureToThrow = failure;
        return this;
    }

    @Override
    public Optional<InvoiceCandidate> searchInvoice(String documentNumber, long amountCents, LocalDate aroundDate) {
        searchInvoiceCalls++;
        failIfDue();
        return invoices.stream()
                .filter(invoice -> invoice.documentNumber().equals(documentNumber))
                .filter(invoice -> invoice.owedAmountCents() == amountCents)
                .filter(invoice -> withinTolerance(invoice.dueDate(), aroundDate))
                .findFirst();
    }

    @Override
    public List<InvoiceCandidate> searchInvoicesByDocument(String documentNumber, LocalDate aroundDate) {
        searchByDocumentCalls++;
        failIfDue();
        return invoices.stream()
                .filter(invoice -> invoice.documentNumber().equals(documentNumber))
                .filter(invoice -> withinTolerance(invoice.dueDate(), aroundDate))
                .toList();
    }

    @Override
    public void recordPayment(long invoiceId, long amountCents, String externalReference, LocalDateTime paidAt) {
        payments.add(new RecordedPayment(invoiceId, amountCents, externalReference, paidAt));
    }

    private void failIfDue() {
        if (failureToThrow != null && totalSearchCalls() > failAfterCalls) {
            throw failureToThrow;
        }
    }

    private static boolean withinTolerance(LocalDate dueDate, LocalDate aroundDate) {
        return Math.abs(ChronoUnit.DAYS.between(dueDate, aroundDate)) <= StatementMatchRule.DATE_TOLERANCE_DAYS;
    }

    public List<RecordedPayment> payments() {
        return List.copyOf(payments);
    }

    public int searchInvoiceCalls() {
        return searchInvoiceCalls;
    }

    public int searchByDocumentCalls() {
        return searchByDocumentCalls;
    }

    public int totalSearchCalls() {
        return searchInvoiceCalls + searchByDocumentCalls;
    }
}
