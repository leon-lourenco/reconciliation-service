package com.cardbilling.reconciliation.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReconciliationMatchJpaRepository extends JpaRepository<ReconciliationMatchEntity, Long> {

    List<ReconciliationMatchEntity> findByRunIdOrderByIdAsc(Long runId);
}
