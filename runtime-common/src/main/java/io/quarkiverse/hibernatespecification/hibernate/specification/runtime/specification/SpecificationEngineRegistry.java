package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

public class SpecificationEngineRegistry {

    private final Map<QueryEngine, SpecificationEngine> engines;

    @Inject
    public SpecificationEngineRegistry(Instance<SpecificationEngine> instances) {
        Map<QueryEngine, SpecificationEngine> map = new EnumMap<>(QueryEngine.class);
        for (SpecificationEngine engine : instances) {
            SpecificationEngine previous = map.put(engine.engine(), engine);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate SpecificationEngine for " + engine.engine()
                                + ": " + previous.getClass().getName()
                                + " and " + engine.getClass().getName());
            }
        }
        this.engines = Map.copyOf(map);
    }

    public SpecificationEngine require(QueryEngine engine) {
        Objects.requireNonNull(engine, "engine must not be null");
        SpecificationEngine impl = engines.get(engine);
        if (impl == null) {
            throw new UnsupportedOperationException(
                    "No SpecificationEngine registered for " + engine
                            + ". Available: " + engines.keySet());
        }
        return impl;
    }

    public boolean isSupported(QueryEngine engine) {
        return engines.containsKey(engine);
    }

    public List<QueryEngine> supportedEngines() {
        return List.copyOf(engines.keySet());
    }
}
