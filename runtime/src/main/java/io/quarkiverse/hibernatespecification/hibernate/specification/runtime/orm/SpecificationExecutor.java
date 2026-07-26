package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.orm;

import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria.CriteriaPredicateFactory;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria.SpecificationQuerySupport;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.PageResponse;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;

public class SpecificationExecutor {

    @Inject
    EntityManager entityManager;

    @Inject
    CriteriaPredicateFactory predicateFactory;

    @Transactional
    public <T> PageResponse<T> findPage(Class<T> entityClass, QueryRequest request) {
        List<T> content = find(entityClass, request);
        long total = count(entityClass, request);
        return PageResponse.of(content, total, request.page());
    }

    @Transactional
    public <T> List<T> find(Class<T> entityClass, QueryRequest request) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        SpecificationQuerySupport.applySortAndWhere(cb, cq, root, request, predicateFactory);

        TypedQuery<T> query = entityManager.createQuery(cq);
        SpecificationQuerySupport.applyPaging(query, request.page());
        return query.getResultList();
    }

    @Transactional
    public <T> long count(Class<T> entityClass, QueryRequest request) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        cq.select(cb.count(root));

        if (Objects.nonNull(request.filter())) {
            cq.where(predicateFactory.build(cb, root, request.filter()));
        }

        return entityManager.createQuery(cq).getSingleResult();
    }
}
