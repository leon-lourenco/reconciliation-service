package com.cardbilling.reconciliation.application;

/**
 * What one statement upload did.
 *
 * @param rowsRead          data rows found in the file (the header row is not one)
 * @param ingested          rows stored as new statement lines
 * @param skippedDuplicates rows whose external reference was already stored, from an earlier
 *                          upload or from a repeat of it inside this same file
 */
public record IngestReport(long rowsRead, long ingested, long skippedDuplicates) {
}
