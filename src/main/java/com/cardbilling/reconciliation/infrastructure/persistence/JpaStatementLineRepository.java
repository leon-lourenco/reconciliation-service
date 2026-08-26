package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.application.port.StatementLineRepositoryPort;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaStatementLineRepository implements StatementLineRepositoryPort {

    private final ExternalStatementLineJpaRepository jpaRepository;

    JpaStatementLineRepository(ExternalStatementLineJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public List<ExternalStatementLine> saveAll(List<ExternalStatementLine> lines) {
        List<ExternalStatementLineEntity> entities = lines.stream()
                .map(ExternalStatementLineEntity::fromDomain)
                .toList();
        return jpaRepository.saveAll(entities).stream().map(ExternalStatementLineEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public ExternalStatementLine save(ExternalStatementLine line) {
        return jpaRepository.save(ExternalStatementLineEntity.fromDomain(line)).toDomain();
    }

    @Override
    public Set<String> findExistingExternalReferences(Collection<String> externalReferences) {
        if (externalReferences.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findExistingExternalReferences(externalReferences);
    }

    @Override
    public List<ExternalStatementLine> findUnmatchedAfter(Long afterId, int limit) {
        List<ExternalStatementLineEntity> page = afterId == null
                ? jpaRepository.findByMatchedFalseOrderByIdAsc(Limit.of(limit))
                : jpaRepository.findByMatchedFalseAndIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit));
        return page.stream().map(ExternalStatementLineEntity::toDomain).toList();
    }

    @Override
    public long countUnmatched() {
        return jpaRepository.countByMatchedFalse();
    }
}
