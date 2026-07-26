package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.reactive;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.hibernate.reactive.mutiny.Mutiny;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria.CriteriaPredicateFactory;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria.SpecificationQuerySupport;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.PageResponse;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;
import io.smallrye.mutiny.Uni;

public class ReactiveSpecificationExecutor {

    private final Mutiny.SessionFactory sessionFactory;
    private final CriteriaPredicateFactory predicateFactory;

    @Inject
    public ReactiveSpecificationExecutor(Mutiny.SessionFactory sessionFactory,
            CriteriaPredicateFactory predicateFactory) {
        this.sessionFactory = sessionFactory;
        this.predicateFactory = predicateFactory;
    }

    public <T> Uni<PageResponse<T>> findPage(Class<T> entityClass, QueryRequest request) {
        return Uni.combine().all()
                .unis(this.find(entityClass, request), this.count(entityClass, request))
                .asTuple()
                .map(tuple -> PageResponse.of(tuple.getItem1(), tuple.getItem2(), request.page()));
    }

    public <T> Uni<PageResponse<T>> findPageInSingleSession(Class<T> entityClass, QueryRequest request) {
        return sessionFactory
                .withSession(session -> buildFindQuery(session, entityClass, request)
                        .map(q -> applyPagination(q, request))
                        .flatMap(Mutiny.SelectionQuery::getResultList)
                        .flatMap(list -> buildCountQuery(session, entityClass, request)
                                .flatMap(Mutiny.SelectionQuery::getSingleResult)
                                .map(total -> PageResponse.of(list, total, request.page()))));
    }

    public <T> Uni<List<T>> find(Class<T> entityClass, QueryRequest request) {
        return sessionFactory.withSession(session -> buildFindQuery(session, entityClass, request)
                .map(query -> applyPagination(query, request))
                .flatMap(Mutiny.SelectionQuery::getResultList));
    }

    public <T> Uni<Long> count(Class<T> entityClass, QueryRequest request) {
        return sessionFactory.withSession(session -> buildCountQuery(session, entityClass, request)
                .flatMap(Mutiny.SelectionQuery::getSingleResult));
    }

    private <T> Uni<Mutiny.SelectionQuery<T>> buildFindQuery(Mutiny.Session session,
            Class<T> entityClass,
            QueryRequest request) {
        return Uni.createFrom().item(() -> {
            CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            SpecificationQuerySupport.applySortAndWhere(cb, cq, root, request, predicateFactory);
            return session.createQuery(cq);
        });
    }

    private <T> Uni<Mutiny.SelectionQuery<Long>> buildCountQuery(Mutiny.Session session,
            Class<T> entityClass,
            QueryRequest request) {
        return Uni.createFrom().item(() -> {
            CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(entityClass);
            cq.select(cb.count(root));
            if (request.filter() != null) {
                cq.where(predicateFactory.build(cb, root, request.filter()));
            }
            return session.createQuery(cq);
        });
    }

    private static <T> Mutiny.SelectionQuery<T> applyPagination(Mutiny.SelectionQuery<T> query,
            QueryRequest request) {
        if (request.page() != null) {
            query.setFirstResult(request.page().offset());
            query.setMaxResults(request.page().size());
        }
        return query;
    }
}
