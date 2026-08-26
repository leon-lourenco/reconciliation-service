package com.cardbilling.reconciliation.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExternalStatementLineJpaRepository extends JpaRepository<ExternalStatementLineEntity, Long> {

    @Query("select line.externalReference from ExternalStatementLineEntity line "
            + "where line.externalReference in :externalReferences")
    Set<String> findExistingExternalReferences(@Param("externalReferences") Collection<String> externalReferences);

    /** First page of unmatched lines. Served by the partial index on (matched = false). */
    List<ExternalStatementLineEntity> findByMatchedFalseOrderByIdAsc(Limit limit);

    /** Subsequent pages, by keyset - see {@code StatementLineRepositoryPort#findUnmatchedAfter}. */
    List<ExternalStatementLineEntity> findByMatchedFalseAndIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    long countByMatchedFalse();
}
