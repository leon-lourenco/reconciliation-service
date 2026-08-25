package com.cardbilling.reconciliation.application.fake;

import com.cardbilling.reconciliation.application.port.ReconciliationRunRepositoryPort;
import com.cardbilling.reconciliation.domain.ReconciliationRun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryReconciliationRunRepository implements ReconciliationRunRepositoryPort {

    private final Map<Long, ReconciliationRun> stored = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public ReconciliationRun save(ReconciliationRun run) {
        ReconciliationRun toStore = run.getId() == null ? run.withId(nextId++) : run;
        stored.put(toStore.getId(), toStore);
        return toStore;
    }

    @Override
    public Optional<ReconciliationRun> findInProgress() {
        return stored.values().stream().filter(ReconciliationRun::isInProgress).findFirst();
    }

    @Override
    public Optional<ReconciliationRun> findById(long id) {
        return Optional.ofNullable(stored.get(id));
    }

    public List<ReconciliationRun> all() {
        return List.copyOf(stored.values());
    }
}
