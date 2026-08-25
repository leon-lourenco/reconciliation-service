package com.cardbilling.reconciliation.application.port;

import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** This service's own store of the statement lines it has ingested. */
public interface StatementLineRepositoryPort {

    List<ExternalStatementLine> saveAll(List<ExternalStatementLine> lines);

    ExternalStatementLine save(ExternalStatementLine line);

    /**
     * Which of these external references are already stored. Lets ingest skip rows it has seen
     * before instead of failing the whole upload on a unique-constraint violation, which is what
     * made the legacy job unsafe to simply re-run.
     */
    Set<String> findExistingExternalReferences(Collection<String> externalReferences);

    /**
     * The next page of not-yet-matched lines, ordered by id, starting after {@code afterId}
     * (null for the first page).
     *
     * <p>Keyset paging rather than an offset: lines that end up NOT_FOUND stay unmatched, so an
     * offset-based "give me the first N unmatched" would hand back the same rows forever.
     */
    List<ExternalStatementLine> findUnmatchedAfter(Long afterId, int limit);

    long countUnmatched();
}
