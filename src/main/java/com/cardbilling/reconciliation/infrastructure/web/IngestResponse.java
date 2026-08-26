package com.cardbilling.reconciliation.infrastructure.web;

import com.cardbilling.reconciliation.application.IngestReport;

/**
 * @param rowsRead          data rows found in the uploaded file
 * @param ingested          rows stored as new statement lines, waiting to be matched
 * @param skippedDuplicates rows whose external reference was already stored - re-uploading a file
 *                          is a no-op rather than an error
 */
public record IngestResponse(long rowsRead, long ingested, long skippedDuplicates) {

    static IngestResponse from(IngestReport report) {
        return new IngestResponse(report.rowsRead(), report.ingested(), report.skippedDuplicates());
    }
}
