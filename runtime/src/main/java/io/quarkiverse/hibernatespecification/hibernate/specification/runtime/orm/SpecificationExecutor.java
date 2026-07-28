package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.orm;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.PageResponse;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification.QueryEngine;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification.SpecificationEngine;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification.SpecificationEngineRegistry;

public class SpecificationExecutor {

    @Inject
    EntityManager entityManager;

    @Inject
    SpecificationEngineRegistry registry;

    @ConfigProperty(name = "quarkus.hibernate-specification.default-engine", defaultValue = "CRITERIA")
    QueryEngine defaultEngine;

    @Transactional
    public <T> List<T> find(Class<T> entityClass, QueryRequest request) {
        return find(entityClass, request, defaultEngine);
    }

    @Transactional
    public <T> long count(Class<T> entityClass, QueryRequest request) {
        return count(entityClass, request, defaultEngine);
    }

    @Transactional
    public <T> PageResponse<T> findPage(Class<T> entityClass, QueryRequest request) {
        return findPage(entityClass, request, defaultEngine);
    }

    @Transactional
    public <T> List<T> find(Class<T> entityClass, QueryRequest request, QueryEngine engine) {
        return resolve(engine).find(entityManager, entityClass, request);
    }

    @Transactional
    public <T> long count(Class<T> entityClass, QueryRequest request, QueryEngine engine) {
        return resolve(engine).count(entityManager, entityClass, request);
    }

    @Transactional
    public <T> PageResponse<T> findPage(Class<T> entityClass, QueryRequest request, QueryEngine engine) {
        List<T> content = find(entityClass, request, engine);
        long total = count(entityClass, request, engine);
        return PageResponse.of(content, total, request.page());
    }

    private SpecificationEngine resolve(QueryEngine engine) {
        return registry.require(engine);
    }
}
