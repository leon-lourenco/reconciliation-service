package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.application.port.ReconciliationRunRepositoryPort;
import com.cardbilling.reconciliation.domain.ReconciliationRun;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaReconciliationRunRepository implements ReconciliationRunRepositoryPort {

    private final ReconciliationRunJpaRepository jpaRepository;

    JpaReconciliationRunRepository(ReconciliationRunJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ReconciliationRun save(ReconciliationRun run) {
        return jpaRepository.save(ReconciliationRunEntity.fromDomain(run)).toDomain();
    }

    @Override
    public Optional<ReconciliationRun> findInProgress() {
        return jpaRepository.findFirstByStatusOrderByIdAsc(ReconciliationRun.Status.RUNNING)
                .map(ReconciliationRunEntity::toDomain);
    }

    @Override
    public Optional<ReconciliationRun> findById(long id) {
        return jpaRepository.findById(id).map(ReconciliationRunEntity::toDomain);
    }
}
