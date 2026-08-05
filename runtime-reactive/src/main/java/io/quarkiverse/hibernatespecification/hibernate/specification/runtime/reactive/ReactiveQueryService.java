package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.reactive;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import org.hibernate.reactive.mutiny.Mutiny;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.DtoMapper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.AbstractQueryExecutor;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.DtoMapperHelper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.JoinContext;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.SpecificationBuilder;
import io.smallrye.mutiny.Uni;

public abstract class ReactiveQueryService<T> extends AbstractQueryExecutor<T> {

    private final Mutiny.SessionFactory sessionFactory;
    private final SpecificationBuilder specBuilder;
    private final DtoMapperHelper dtoMapperHelper;
    private final Class<T> entityClass;

    protected ReactiveQueryService(Mutiny.SessionFactory sessionFactory, SpecificationBuilder specBuilder,
            DtoMapperHelper dtoMapperHelper, Class<T> entityClass) {
        super(specBuilder, dtoMapperHelper, entityClass);
        this.sessionFactory = Objects.requireNonNull(sessionFactory);
        this.specBuilder = Objects.requireNonNull(specBuilder);
        this.dtoMapperHelper = Objects.requireNonNull(dtoMapperHelper);
        this.entityClass = Objects.requireNonNull(entityClass);
    }

    protected abstract QueryRequest initFiltersAndSorts();

    public <R> Uni<PageResponse<R>> query(QueryRequest request, Class<R> dtoOrEntity) {
        final QueryRequest safeRequest = request != null ? request : QueryRequest.of(null);
        final PageRequest pageReq = safeRequest.page();
        final int page = Math.max(0, pageReq.page());
        final int size = pageReq.size();

        final boolean isDtoProjection = dtoOrEntity != null
                && dtoOrEntity.isAnnotationPresent(DtoMapper.class);

        @SuppressWarnings("unchecked")
        final Class<T> rootEntity = isDtoProjection
                ? (Class<T>) specBuilder.resolveEntityClass(dtoOrEntity)
                : entityClass;

        final QueryRequest internalRequest = initFiltersAndSorts();
        final QueryRequest clientOnlyRequest = safeRequest.filter() == null
                ? null
                : new QueryRequest(safeRequest.filter(), List.of(), PageRequest.firstPage());

        if (isDtoProjection) {
            return executeProjected(safeRequest, clientOnlyRequest, internalRequest,
                    page, size, dtoOrEntity, rootEntity);
        }
        return executeEntity(safeRequest, clientOnlyRequest, internalRequest,
                page, size, rootEntity);
    }

    @SuppressWarnings("unchecked")
    private <R> Uni<PageResponse<R>> executeEntity(QueryRequest request, QueryRequest clientOnlyRequest,
            QueryRequest internalRequest, int page, int size, Class<T> rootEntity) {

        return sessionFactory.withSession(session -> Uni.combine().all()
                .unis(fetchEntities(session, request, clientOnlyRequest, internalRequest, page, size, rootEntity),
                        count(session, clientOnlyRequest, internalRequest, null, rootEntity))
                .asTuple()
                .map(tuple -> PageResponse.of(
                        (List<R>) tuple.getItem1(),
                        tuple.getItem2(),
                        new PageRequest(page, size))));
    }

    private Uni<List<T>> fetchEntities(Mutiny.Session session, QueryRequest request,
            QueryRequest clientOnlyRequest, QueryRequest internalRequest, int page, int size,
            Class<T> rootEntity) {

        final JoinContext joinCtx = new JoinContext();

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
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    private <D> Uni<PageResponse<D>> executeProjected(QueryRequest request, QueryRequest clientOnlyRequest,
            QueryRequest internalRequest, int page, int size, Class<?> dtoClass, Class<T> rootEntity) {

        return sessionFactory.withSession(session -> Uni.combine().all()
                .unis(fetchProjected(session, request, clientOnlyRequest, internalRequest, page, size,
                        dtoClass, rootEntity),
                        count(session, clientOnlyRequest, internalRequest, dtoClass, rootEntity))
                .asTuple()
                .map(tuple -> {
                    final List<Tuple> tuples = tuple.getItem1();
                    long total = tuple.getItem2();

                    if (page == 0 && tuples.size() < size) {
                        total = tuples.size();
                    }

                    final List<FieldMeta> metas = specBuilder.getProjectionFieldMetas(dtoClass);
                    final List<D> content = tuples.stream()
                            .map(t -> dtoMapperHelper.mapTupleToDto(t, (Class<D>) dtoClass, metas))
                            .collect(Collectors.toList());

                    return PageResponse.of(content, total, new PageRequest(page, size));
                }));
    }

    private Uni<List<Tuple>> fetchProjected(Mutiny.Session session, QueryRequest request,
            QueryRequest clientOnlyRequest, QueryRequest internalRequest, int page, int size,
            Class<?> dtoClass, Class<T> rootEntity) {

        final JoinContext joinCtx = new JoinContext();

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = buildClientPredicate(clientOnlyRequest,
                dtoClass, joinCtx);

        final Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = buildInternalPredicate(
                internalRequest, joinCtx);

        final CriteriaBuilder cb = sessionFactory.getCriteriaBuilder();
        final CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        final Root<T> root = cq.from(rootEntity);

        final List<FieldMeta> metas = specBuilder.getProjectionFieldMetas(dtoClass);
        final List<Selection<?>> selections = metas.stream()
                .map(m -> {
                    Path<?> path = specBuilder.resolvePathWithJoin(root, m.entityPath(), joinCtx);
                    return path.alias(SpecificationBuilder.sanitizeAlias(m.dtoName()));
                })
                .collect(Collectors.toList());

        cq.multiselect(selections);

        if (joinCtx.hasCollectionJoin()) {
            cq.distinct(true);
        }

        final SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(root, cq, cb);

        applyWhere(cq, ctx, clientPred, internalPred);
        applySort(cq, root, cb, request.sort(), dtoClass, joinCtx);

        return session.createQuery(cq)
                .setFirstResult(page * size)
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
