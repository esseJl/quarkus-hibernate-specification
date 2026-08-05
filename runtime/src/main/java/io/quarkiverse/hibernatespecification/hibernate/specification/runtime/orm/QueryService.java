package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.orm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.DtoMapper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.AbstractQueryExecutor;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.DtoMapperHelper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.JoinContext;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.SpecificationBuilder;

public abstract class QueryService<T> extends AbstractQueryExecutor<T> {

    private final EntityManager entityManager;

    protected QueryService(EntityManager entityManager, SpecificationBuilder specBuilder, DtoMapperHelper dtoMapperHelper,
            Class<T> entityClass) {
        super(specBuilder, dtoMapperHelper, entityClass);
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    protected abstract QueryRequest initFiltersAndSorts();

    @Transactional(TxType.SUPPORTS)
    @SuppressWarnings("unchecked")
    public <R> PageResponse<R> query(QueryRequest request, Class<R> dtoOrEntity) {
        QueryRequest safeRequest = request != null ? request : QueryRequest.of(null);
        PageRequest pageReq = safeRequest.page();
        int page = pageReq.page();
        int size = pageReq.size();
        int offset = pageReq.offset();

        boolean isDtoProjection = dtoOrEntity != null && dtoOrEntity.isAnnotationPresent(DtoMapper.class);
        Class<T> rootEntity = isDtoProjection
                ? (Class<T>) specBuilder.resolveEntityClass(dtoOrEntity)
                : entityClass;

        JoinContext joinCtx = new JoinContext();

        QueryRequest internalRequest = initFiltersAndSorts();
        Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = specBuilder
                .buildPredicateWithJoin(internalRequest, null, joinCtx);

        Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = safeRequest.filter() == null ? null
                : specBuilder.buildPredicateWithJoin(
                        new QueryRequest(safeRequest.filter(), List.of(), PageRequest.firstPage()),
                        dtoOrEntity, joinCtx);

        if (isDtoProjection) {
            return findProjected(safeRequest, clientPred, internalPred, joinCtx, page, size, offset,
                    dtoOrEntity, rootEntity, internalRequest);
        }
        return (PageResponse<R>) findEntities(safeRequest, clientPred, internalPred, joinCtx,
                page, size, offset, rootEntity, internalRequest);
    }

    private PageResponse<T> findEntities(QueryRequest request,
            Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred,
            Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred,
            JoinContext joinCtx, int page, int size, int offset, Class<T> rootEntity, QueryRequest internalRequest) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(rootEntity);
        Root<T> root = cq.from(rootEntity);

        SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(root, cq, cb);

        applyWhere(cq, ctx, clientPred, internalPred);
        applySort(cq, root, cb, request.sort(), null, joinCtx);

        if (joinCtx.hasCollectionJoin()) {
            cq.distinct(true);
        }

        TypedQuery<T> dataQuery = entityManager.createQuery(cq);
        dataQuery.setFirstResult(offset);
        dataQuery.setMaxResults(size);
        List<T> content = dataQuery.getResultList();

        // FIX (سازگاری/بهینه‌سازی): همان shortcut موجود در findProjected این‌جا هم اعمال شد.
        // اگر صفحه‌ی اول باشد و تعداد نتایج کمتر از سایزِ صفحه باشد (و JOINِ
        // collection‌ای در کار نباشد)، دیگر نیازی به یک کوئری COUNT جداگانه نیست؛
        // چون totalElements دقیقاً همان content.size() است.
        long total;
        if (page == 0 && content.size() < size && !joinCtx.hasCollectionJoin()) {
            total = content.size();
        } else {
            total = count(
                    request.filter() == null ? null
                            : new QueryRequest(request.filter(), List.of(), PageRequest.firstPage()),
                    internalRequest,
                    null,
                    rootEntity);
        }

        return PageResponse.of(content, total, new PageRequest(page, size));
    }

    @SuppressWarnings("unchecked")
    private <D> PageResponse<D> findProjected(QueryRequest request,
            Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred,
            Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred,
            JoinContext joinCtx, int page, int size, int offset, Class<?> dtoClass, Class<T> rootEntity,
            QueryRequest internalRequest) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<T> root = cq.from(rootEntity);

        List<FieldMeta> metas = specBuilder.getProjectionFieldMetas(dtoClass);
        List<Selection<?>> selections = new ArrayList<>(metas.size());
        for (FieldMeta m : metas) {
            Path<?> path = specBuilder.resolvePathWithJoin(root, m.entityPath(), joinCtx);
            selections.add(path.alias(SpecificationBuilder.sanitizeAlias(m.dtoName())));
        }
        cq.multiselect(selections);

        if (joinCtx.hasCollectionJoin()) {
            cq.distinct(true);
        }

        SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(root, cq, cb);

        applyWhere(cq, ctx, clientPred, internalPred);
        applySort(cq, root, cb, request.sort(), dtoClass, joinCtx);

        TypedQuery<Tuple> dataQuery = entityManager.createQuery(cq);
        dataQuery.setFirstResult(offset);
        dataQuery.setMaxResults(size);
        List<Tuple> tuples = dataQuery.getResultList();

        long total;
        if (page == 0 && tuples.size() < size && !joinCtx.hasCollectionJoin()) {
            total = tuples.size();
        } else {
            total = count(request.filter() == null ? null
                    : new QueryRequest(request.filter(), List.of(), PageRequest.firstPage()), internalRequest, dtoClass,
                    rootEntity);
        }

        List<D> content = tuples.stream()
                .map(tuple -> dtoMapperHelper.mapTupleToDto(tuple, (Class<D>) dtoClass, metas))
                .collect(Collectors.toList());

        return PageResponse.of(content, total, new PageRequest(page, size));
    }

    private long count(QueryRequest clientRequest, QueryRequest internalRequest,
            Class<?> dtoOrEntity, Class<T> rootEntity) {

        JoinContext countJoinCtx = new JoinContext();

        Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred = clientRequest == null ? null
                : specBuilder.buildPredicateWithJoin(clientRequest, dtoOrEntity, countJoinCtx);

        Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred = internalRequest == null ? null
                : specBuilder.buildPredicateWithJoin(internalRequest, null, countJoinCtx);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<T> countRoot = countCq.from(rootEntity);

        countCq.select(countJoinCtx.hasCollectionJoin()
                ? cb.countDistinct(countRoot)
                : cb.count(countRoot));

        SpecificationBuilder.CriteriaContext<T> ctx = new SpecificationBuilder.CriteriaContext<>(countRoot, countCq, cb);

        List<Predicate> preds = new ArrayList<>(2);
        if (clientPred != null) {
            preds.add(clientPred.apply(ctx));
        }
        if (internalPred != null) {
            preds.add(internalPred.apply(ctx));
        }
        if (!preds.isEmpty()) {
            countCq.where(preds.toArray(Predicate[]::new));
        }

        return entityManager.createQuery(countCq).getSingleResult();
    }

}