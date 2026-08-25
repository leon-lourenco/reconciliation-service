package com.cardbilling.reconciliation.application.fake;

import com.cardbilling.reconciliation.application.port.ReconciliationMatchRepositoryPort;
import com.cardbilling.reconciliation.domain.ReconciliationMatch;
import java.util.ArrayList;
import java.util.List;

public class InMemoryReconciliationMatchRepository implements ReconciliationMatchRepositoryPort {

    private final List<ReconciliationMatch> stored = new ArrayList<>();
    private long nextId = 1;

    @Override
    public ReconciliationMatch save(ReconciliationMatch match) {
        ReconciliationMatch toStore = match.withId(nextId++);
        stored.add(toStore);
        return toStore;
    }

    @Override
    public List<ReconciliationMatch> findByRunId(long runId) {
        return stored.stream().filter(match -> match.getRunId() != null && match.getRunId() == runId).toList();
    }

    public List<ReconciliationMatch> all() {
        return List.copyOf(stored);
    }
}
