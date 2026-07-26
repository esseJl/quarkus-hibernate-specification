package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;

public final class CriteriaPathResolver {

    private CriteriaPathResolver() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Path<T> resolve(From<?, ?> root, String fieldPath) {
        Objects.requireNonNull(fieldPath, "field path must not be null");
        if (fieldPath.isBlank()) {
            throw new IllegalArgumentException("field path must not be blank");
        }

        String[] parts = fieldPath.split("\\.");
        if (parts.length == 1) {
            return (Path<T>) root.get(parts[0]);
        }

        record State(Path<?> path, From<?, ?> from, Map<String, From<?, ?>> cache, String prefix) {
        }

        State initial = new State(root, root, Map.of("", root), "");

        State finalState = Stream.of(parts)
                .reduce(initial, (state, part) -> {
                    String key = state.prefix().isEmpty() ? part : state.prefix() + "." + part;
                    boolean isLast = key.equals(fieldPath);

                    if (isLast) {
                        return new State(state.path().get(part), state.from(), state.cache(), key);
                    }

                    From<?, ?> cached = state.cache().get(key);
                    if (cached != null) {
                        return new State(cached, cached, state.cache(), key);
                    }

                    if (isAssociation(state.from(), part)) {
                        Join<?, ?> join = state.from().join(part, JoinType.LEFT);
                        return new State(join, join, Map.copyOf(Map.of(key, join)), key);
                    }

                    Path<?> next = state.path().get(part);
                    if (next instanceof From<?, ?> f) {
                        return new State(next, f, Map.copyOf(Map.of(key, f)), key);
                    }
                    return new State(next, state.from(), state.cache(), key);
                }, (a, b) -> b);

        return (Path<T>) finalState.path();
    }

    private static boolean isAssociation(From<?, ?> from, String attributeName) {
        try {
            if (!(from.getModel() instanceof ManagedType<?> managed)) {
                return false;
            }
            Attribute<?, ?> attr = managed.getAttribute(attributeName);
            return attr.isAssociation() || attr instanceof PluralAttribute;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}