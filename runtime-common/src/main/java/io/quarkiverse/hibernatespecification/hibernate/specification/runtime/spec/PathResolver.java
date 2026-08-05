package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.exception.SpecificationException;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FieldMeta;

@ApplicationScoped
public class PathResolver {

    private final FieldMetaRegistry fieldMetaRegistry;

    @Inject
    public PathResolver(FieldMetaRegistry fieldMetaRegistry) {
        this.fieldMetaRegistry = fieldMetaRegistry;
    }

    public Path<?> resolvePath(Root<?> root, String clientField, Class<?> dtoOrEntity, JoinContext ctx) {
        FieldMeta meta = fieldMetaRegistry.requireAllowed(clientField, dtoOrEntity, "filter");
        return resolvePathWithJoin(root, meta.entityPath(), ctx);
    }

    public Path<?> resolvePathWithJoin(From<?, ?> from, String entityPath, JoinContext ctx) {
        if (entityPath == null || entityPath.isBlank()) {
            throw new SpecificationException("Path must not be blank");
        }
        if (ctx == null) {
            throw new IllegalArgumentException("JoinContext must not be null");
        }
        try {
            return ctx.resolve(from, entityPath);
        } catch (IllegalArgumentException ex) {
            throw new SpecificationException(
                    "Unknown path segment in field '" + entityPath + "': " + ex.getMessage(), ex);
        }
    }

    public String resolveSortPath(String clientField, Class<?> dtoOrEntity) {
        return fieldMetaRegistry.requireAllowed(clientField, dtoOrEntity, "sorting").entityPath();
    }
}