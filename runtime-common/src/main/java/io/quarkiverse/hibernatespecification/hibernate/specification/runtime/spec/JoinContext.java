package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;

/**
 * Shared context for a single CriteriaQuery.
 * Must be created per-query (never singleton/shared).
 * Not thread-safe (lifecycle = one query).
 */
public final class JoinContext {

    private final Map<String, From<?, ?>> joins = new HashMap<>();
    private boolean hasCollectionJoin = false;

    /**
     * Resolve dotted entity path with LEFT JOIN and reuse previous joins.
     * Example: "user.profile.city" → creates/reuses joins for user and user.profile,
     * then returns Path for city.
     */
    public Path<?> resolve(From<?, ?> root, String entityPath) {
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
            // some providers may not expose attribute
        }
    }

    public boolean hasCollectionJoin() {
        return hasCollectionJoin;
    }

    public int joinCount() {
        return joins.size();
    }
}
