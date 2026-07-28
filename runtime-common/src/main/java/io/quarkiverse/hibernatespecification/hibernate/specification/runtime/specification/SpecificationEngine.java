package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification;

import java.util.List;

import jakarta.persistence.EntityManager;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;

public interface SpecificationEngine {

    QueryEngine engine();

    <T> List<T> find(EntityManager em, Class<T> entityClass, QueryRequest request);

    <T> long count(EntityManager em, Class<T> entityClass, QueryRequest request);
}
