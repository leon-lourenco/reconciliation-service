package com.cardbilling.reconciliation.infrastructure.web;

import com.cardbilling.reconciliation.application.IngestStatementUseCase;
import com.cardbilling.reconciliation.application.MatchStatementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/statements")
@Tag(name = "statements", description = "External statement ingest and matching")
public class StatementsController {

    private final IngestStatementUseCase ingestStatement;
    private final MatchStatementUseCase matchStatement;

    StatementsController(IngestStatementUseCase ingestStatement, MatchStatementUseCase matchStatement) {
        this.ingestStatement = ingestStatement;
        this.matchStatement = matchStatement;
    }

    @PostMapping(path = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest an external statement file",
            description = """
                    CSV, same columns as the legacy: external_reference,document_number,amount_cents,
                    statement_date. Rows are streamed and stored unmatched; a row whose external
                    reference is already stored is skipped, so re-uploading the same file is a no-op.""")
    public IngestResponse ingest(@RequestPart("file") MultipartFile file) throws IOException {
        // The file is handed over as a stream and never read into a list - see
        // IngestStatementUseCase, which is where the legacy's readAll() got fixed.
        try (InputStream csv = file.getInputStream()) {
            return IngestResponse.from(ingestStatement.ingest(csv));
        }
    }

    @PostMapping("/match")
    @Operation(summary = "Reconcile every unmatched statement line",
            description = """
                    One indexed lookup against billing-service per statement line, instead of loading
                    every open invoice and scanning. A match records a payment on billing-service,
                    keyed by the statement line's external reference so a replay is a no-op.""")
    public ReconciliationRunResponse match() {
        return ReconciliationRunResponse.from(matchStatement.run());
    }
}
