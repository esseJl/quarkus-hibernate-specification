package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.exception.SpecificationException;

/**
 * Shared context for a single CriteriaQuery.
 * Must be created per-query (never singleton/shared).
 * Not thread-safe (lifecycle = one query).
 */
public final class JoinContext {

    private final Map<String, From<?, ?>> joins = new HashMap<>();
    private boolean hasCollectionJoin = false;
    private final int maxJoins;

    public JoinContext() {
        this(Integer.MAX_VALUE);
    }

    public JoinContext(int maxJoins) {
        if (maxJoins <= 0) {
            throw new IllegalArgumentException("maxJoins must be > 0");
        }
        this.maxJoins = maxJoins;
    }

    public Path<?> resolve(From<?, ?> root, String entityPath) {
        Objects.requireNonNull(root, "root must not be null");
        if (entityPath == null || entityPath.isBlank()) {
            throw new IllegalArgumentException("entityPath must not be blank");
        }

        String[] parts = entityPath.split("\\.");
        From<?, ?> current = root;
        StringBuilder pathKey = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (pathKey.length() > 0) {
                pathKey.append('.');
            }
            pathKey.append(part);

            boolean isLast = (i == parts.length - 1);

            if (!isLast) {
                String key = pathKey.toString();
                From<?, ?> existing = joins.get(key);
                if (existing != null) {
                    current = existing;
                } else {
                    if (joins.size() >= maxJoins) {
                        throw new SpecificationException(
                                "Query exceeds maximum allowed number of joins per query: " + maxJoins);
                    }
                    Join<?, ?> join = current.join(part, JoinType.LEFT);
                    markIfCollection(join);
                    joins.put(key, join);
                    current = join;
                }
            } else {
                return current.get(part);
            }
        }
        return current;
    }

    private void markIfCollection(Join<?, ?> join) {
        try {
            Attribute<?, ?> attr = join.getAttribute();
            if (attr instanceof PluralAttribute) {
                hasCollectionJoin = true;
            }
        } catch (Exception ignored) {
        }
    }

    public boolean hasCollectionJoin() {
        return hasCollectionJoin;
    }

    public int joinCount() {
        return joins.size();
    }

    public void clear() {
        joins.clear();
        hasCollectionJoin = false;
    }
}