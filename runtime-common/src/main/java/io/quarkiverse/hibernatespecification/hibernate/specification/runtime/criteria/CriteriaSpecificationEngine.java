package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria;

import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification.QueryEngine;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification.SpecificationEngine;

public class CriteriaSpecificationEngine implements SpecificationEngine {

    private final CriteriaPredicateFactory predicateFactory;

    @Inject
    public CriteriaSpecificationEngine(CriteriaPredicateFactory predicateFactory) {
        this.predicateFactory = Objects.requireNonNull(predicateFactory);
    }

    @Override
    public QueryEngine engine() {
        return QueryEngine.CRITERIA;
    }

    @Override
    public <T> List<T> find(EntityManager em, Class<T> entityClass, QueryRequest request) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        SpecificationQuerySupport.applySortAndWhere(cb, cq, root, request, predicateFactory);

        TypedQuery<T> query = em.createQuery(cq);
        SpecificationQuerySupport.applyPaging(query, request.page());
        return query.getResultList();
    }

    @Override
    public <T> long count(EntityManager em, Class<T> entityClass, QueryRequest request) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        cq.select(cb.count(root));

        if (request.filter() != null) {
            cq.where(predicateFactory.build(cb, root, request.filter()));
        }
        return em.createQuery(cq).getSingleResult();
    }
}
