package com.cardbilling.reconciliation.application.fake;

import com.cardbilling.reconciliation.application.port.StatementLineRepositoryPort;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stand-in for the persistence adapter, so the use cases can be exercised with no Spring context
 * and no database. Also records how big the batches it was handed were, which is how the streaming
 * behaviour gets asserted rather than assumed.
 */
public class InMemoryStatementLineRepository implements StatementLineRepositoryPort {

    private final Map<Long, ExternalStatementLine> stored = new LinkedHashMap<>();
    private final List<Integer> saveAllBatchSizes = new ArrayList<>();
    private long nextId = 1;

    @Override
    public List<ExternalStatementLine> saveAll(List<ExternalStatementLine> lines) {
        saveAllBatchSizes.add(lines.size());
        return lines.stream().map(this::save).toList();
    }

    @Override
    public ExternalStatementLine save(ExternalStatementLine line) {
        ExternalStatementLine toStore = line.getId() == null ? line.withId(nextId++) : line;
        stored.put(toStore.getId(), toStore);
        return toStore;
    }

    @Override
    public Set<String> findExistingExternalReferences(Collection<String> externalReferences) {
        return stored.values().stream()
                .map(ExternalStatementLine::getExternalReference)
                .filter(externalReferences::contains)
                .collect(Collectors.toSet());
    }

    @Override
    public List<ExternalStatementLine> findUnmatchedAfter(Long afterId, int limit) {
        return stored.values().stream()
                .filter(line -> !line.isMatched())
                .filter(line -> afterId == null || line.getId() > afterId)
                .sorted(Comparator.comparing(ExternalStatementLine::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public long countUnmatched() {
        return stored.values().stream().filter(line -> !line.isMatched()).count();
    }

    public List<ExternalStatementLine> all() {
        return List.copyOf(stored.values());
    }

    public List<Integer> saveAllBatchSizes() {
        return List.copyOf(saveAllBatchSizes);
    }
}
