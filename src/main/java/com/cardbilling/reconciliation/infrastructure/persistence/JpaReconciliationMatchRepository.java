package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.application.port.ReconciliationMatchRepositoryPort;
import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaReconciliationMatchRepository implements ReconciliationMatchRepositoryPort {

    private final ReconciliationMatchJpaRepository jpaRepository;

    JpaReconciliationMatchRepository(ReconciliationMatchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ReconciliationMatch save(ReconciliationMatch match) {
        return jpaRepository.save(ReconciliationMatchEntity.fromDomain(match)).toDomain();
    }

    @Override
    public List<ReconciliationMatch> findByRunId(long runId) {
        return jpaRepository.findByRunIdOrderByIdAsc(runId).stream()
                .map(ReconciliationMatchEntity::toDomain)
                .toList();
    }
}
