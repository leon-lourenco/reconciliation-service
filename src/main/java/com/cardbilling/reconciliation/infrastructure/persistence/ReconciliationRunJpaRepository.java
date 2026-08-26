package com.cardbilling.reconciliation.infrastructure.persistence;

import com.cardbilling.reconciliation.domain.ReconciliationRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReconciliationRunJpaRepository extends JpaRepository<ReconciliationRunEntity, Long> {

    Optional<ReconciliationRunEntity> findFirstByStatusOrderByIdAsc(ReconciliationRun.Status status);
}
