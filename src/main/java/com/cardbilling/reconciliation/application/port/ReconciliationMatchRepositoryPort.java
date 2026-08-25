package com.cardbilling.reconciliation.application.port;

import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import java.util.List;

/** This service's own record of what each statement line turned out to be, per run. */
public interface ReconciliationMatchRepositoryPort {

    ReconciliationMatch save(ReconciliationMatch match);

    List<ReconciliationMatch> findByRunId(long runId);
}
