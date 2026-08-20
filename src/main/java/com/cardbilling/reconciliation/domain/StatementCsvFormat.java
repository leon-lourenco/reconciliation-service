package com.cardbilling.reconciliation.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The external statement file's column contract, unchanged from the legacy monolith:
 * {@code external_reference,document_number,amount_cents,statement_date} with a header row.
 *
 * <p>The format is part of this bounded context's language, not a detail of whichever CSV library
 * happens to read the file, so it lives here: the adapter in {@code infrastructure.persistence}
 * hands over an already-split row and this decides whether that row is a statement line.
 */
public final class StatementCsvFormat {

    public static final String[] HEADER = {"external_reference", "document_number", "amount_cents", "statement_date"};

    private static final int EXTERNAL_REFERENCE = 0;
    private static final int DOCUMENT_NUMBER = 1;
    private static final int AMOUNT_CENTS = 2;
    private static final int STATEMENT_DATE = 3;

    private StatementCsvFormat() {
    }

    public static boolean isHeaderRow(String[] columns) {
        return columns.length > 0 && HEADER[EXTERNAL_REFERENCE].equalsIgnoreCase(columns[EXTERNAL_REFERENCE].trim());
    }

    /**
     * @param lineNumber the row's 1-based position in the file, so a rejection points at something
     *                   a human can actually find in the uploaded file.
     */
    public static ExternalStatementLine parse(String[] columns, long lineNumber) {
        if (columns.length != HEADER.length) {
            throw new MalformedStatementLineException("line " + lineNumber + ": expected " + HEADER.length
                    + " columns, found " + columns.length);
        }
        long amountCents;
        try {
            amountCents = Long.parseLong(columns[AMOUNT_CENTS].trim());
        } catch (NumberFormatException e) {
            throw new MalformedStatementLineException(
                    "line " + lineNumber + ": amount_cents is not a whole number: '" + columns[AMOUNT_CENTS] + "'");
        }
        LocalDate statementDate;
        try {
            statementDate = LocalDate.parse(columns[STATEMENT_DATE].trim());
        } catch (DateTimeParseException e) {
            throw new MalformedStatementLineException(
                    "line " + lineNumber + ": statement_date is not an ISO date: '" + columns[STATEMENT_DATE] + "'");
        }
        try {
            return ExternalStatementLine.ingested(columns[EXTERNAL_REFERENCE].trim(),
                    columns[DOCUMENT_NUMBER].trim(), amountCents, statementDate, String.join(",", columns));
        } catch (MalformedStatementLineException e) {
            throw new MalformedStatementLineException("line " + lineNumber + ": " + e.getMessage());
        }
    }
}
