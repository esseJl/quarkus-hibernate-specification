package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.reactive;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import org.hibernate.reactive.mutiny.Mutiny;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.AbstractQueryExecutor;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.DtoMapperHelper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.JoinContext;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.SpecificationBuilder;
import io.smallrye.mutiny.Uni;

public abstract class ReactiveQueryService<T> extends AbstractQueryExecutor<T> {

    private final Mutiny.SessionFactory sessionFactory;

    protected ReactiveQueryService(Mutiny.SessionFactory sessionFactory, SpecificationBuilder specBuilder,
            DtoMapperHelper dtoMapperHelper, Class<T> entityClass) {
        super(specBuilder, dtoMapperHelper, entityClass);
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory must not be null");
    }

    protected abstract QueryRequest initFiltersAndSorts();

    public <R> Uni<PageResponse<R>> query(QueryRequest request, Class<R> dtoOrEntity) {
        PreparedQueryContext<T> ctx = prepare(request, dtoOrEntity);
        int offset = ctx.safeRequest().page().offset();

        JoinContext joinCtx = new JoinContext();
        Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = buildInternalPredicate(
                ctx.internalRequest(), joinCtx);
        Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = buildClientPredicate(ctx.clientOnlyRequest(),
                dtoOrEntity, joinCtx);

        if (ctx.isDtoProjection()) {
            return executeProjected(ctx.safeRequest(), ctx.clientOnlyRequest(), ctx.internalRequest(),
                    ctx.page(), ctx.size(), offset, dtoOrEntity, ctx.rootEntity());
        }
        return executeEntity(ctx.safeRequest(), ctx.clientOnlyRequest(), ctx.internalRequest(),
                ctx.page(), ctx.size(), offset, ctx.rootEntity());
    }

    @SuppressWarnings("unchecked")
    private <R> Uni<PageResponse<R>> executeEntity(QueryRequest request, QueryRequest clientOnlyRequest,
            QueryRequest internalRequest, int page, int size, int offset, Class<T> rootEntity) {

        return sessionFactory.withSession(session -> {
            final JoinContext joinCtx = new JoinContext();
            return fetchEntities(session, request, clientOnlyRequest, internalRequest, offset, size, rootEntity, joinCtx)
                    .chain(items -> {
                        if (page == 0 && items.size() < size && !joinCtx.hasCollectionJoin()) {
                            return Uni.createFrom().item(
                                    PageResponse.of((List<R>) items, items.size(), new PageRequest(page, size)));
                        }
                        return count(session, clientOnlyRequest, internalRequest, null, rootEntity)
                                .map(total -> PageResponse.of((List<R>) items, total, new PageRequest(page, size)));
                    });
        });
    }

    private Uni<List<T>> fetchEntities(Mutiny.Session session, QueryRequest request,
            QueryRequest clientOnlyRequest, QueryRequest internalRequest, int offset, int size,
            Class<T> rootEntity, JoinContext joinCtx) {

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = buildClientPredicate(clientOnlyRequest,
                null, joinCtx);

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = buildInternalPredicate(
                internalRequest, joinCtx);

        final CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        final CriteriaQuery<T> cq = cb.createQuery(rootEntity);
        final Root<T> root = cq.from(rootEntity);
        final SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(root, cq, cb);

        applyWhere(cq, ctx, clientPred, internalPred);
        applySort(cq, root, cb, request.sort(), null, joinCtx);

        if (joinCtx.hasCollectionJoin()) {
            cq.distinct(true);
        }

        return session.createQuery(cq)
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList();
    }

    private <D> Uni<PageResponse<D>> executeProjected(QueryRequest request, QueryRequest clientOnlyRequest,
            QueryRequest internalRequest, int page, int size, int offset, Class<?> dtoClass, Class<T> rootEntity) {

        return sessionFactory.withSession(session -> {
            final JoinContext joinCtx = new JoinContext();
            return fetchProjected(session, request, clientOnlyRequest, internalRequest, offset, size, dtoClass, rootEntity,
                    joinCtx)
                    .chain(tuples -> {
                        final List<FieldMeta> metas = specBuilder.getProjectionFieldMetas(dtoClass);

                        if (page == 0 && tuples.size() < size && !joinCtx.hasCollectionJoin()) {
                            @SuppressWarnings("unchecked")
                            final List<D> content = tuples.stream()
                                    .map(t -> dtoMapperHelper.mapTupleToDto(t, (Class<D>) dtoClass, metas))
                                    .collect(Collectors.toList());
                            return Uni.createFrom().item(
                                    PageResponse.of(content, tuples.size(), new PageRequest(page, size)));
                        }

                        return count(session, clientOnlyRequest, internalRequest, dtoClass, rootEntity)
                                .map(total -> {
                                    @SuppressWarnings("unchecked")
                                    final List<D> content = tuples.stream()
                                            .map(t -> dtoMapperHelper.mapTupleToDto(t, (Class<D>) dtoClass, metas))
                                            .collect(Collectors.toList());

                                    return PageResponse.of(content, total, new PageRequest(page, size));
                                });
                    });
        });
    }

    private Uni<List<Tuple>> fetchProjected(Mutiny.Session session, QueryRequest request,
            QueryRequest clientOnlyRequest, QueryRequest internalRequest, int offset, int size,
            Class<?> dtoClass, Class<T> rootEntity, JoinContext joinCtx) {

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = buildClientPredicate(clientOnlyRequest,
                dtoClass, joinCtx);

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = buildInternalPredicate(
                internalRequest, joinCtx);

        final CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        final CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        final Root<T> root = cq.from(rootEntity);

        //final List<FieldMeta> metas = specBuilder.getProjectionFieldMetas(dtoClass);

        cq.multiselect(specBuilder.buildProjectionSelectionsWithAlias(root, dtoClass, joinCtx));

        if (joinCtx.hasCollectionJoin()) {
            cq.distinct(true);
        }

        final SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(root, cq, cb);

        applyWhere(cq, ctx, clientPred, internalPred);
        applySort(cq, root, cb, request.sort(), dtoClass, joinCtx);

        return session.createQuery(cq)
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList();
    }

    private Uni<Long> count(Mutiny.Session session, QueryRequest clientRequest,
            QueryRequest internalRequest, Class<?> dtoOrEntity, Class<T> rootEntity) {

        final JoinContext countJoinCtx = new JoinContext();

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = buildClientPredicate(clientRequest,
                dtoOrEntity, countJoinCtx);

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = buildInternalPredicate(
                internalRequest, countJoinCtx);

        final CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        final CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        final Root<T> countRoot = countCq.from(rootEntity);

        countCq.select(countJoinCtx.hasCollectionJoin()
                ? cb.countDistinct(countRoot)
                : cb.count(countRoot));

        final SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(countRoot, countCq, cb);

        applyWhere(countCq, ctx, clientPred, internalPred);

        return session.createQuery(countCq).getSingleResult();
    }

}
