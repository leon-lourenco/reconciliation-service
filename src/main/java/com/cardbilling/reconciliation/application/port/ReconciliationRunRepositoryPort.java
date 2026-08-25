package com.cardbilling.reconciliation.application.port;

import com.cardbilling.reconciliation.domain.ReconciliationRun;
import java.util.Optional;

/** This service's own audit trail of reconciliation runs. */
public interface ReconciliationRunRepositoryPort {

    ReconciliationRun save(ReconciliationRun run);

    /** A run still marked RUNNING, if there is one - at most one run may be in flight. */
    Optional<ReconciliationRun> findInProgress();

    Optional<ReconciliationRun> findById(long id);
}
