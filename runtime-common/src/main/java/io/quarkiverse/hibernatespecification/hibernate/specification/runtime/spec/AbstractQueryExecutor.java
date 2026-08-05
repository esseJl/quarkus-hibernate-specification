package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.*;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.DtoMapper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;

public abstract class AbstractQueryExecutor<T> {

    protected final SpecificationBuilder specBuilder;
    protected final DtoMapperHelper dtoMapperHelper;
    protected final Class<T> entityClass;

    protected AbstractQueryExecutor(SpecificationBuilder specBuilder, DtoMapperHelper dtoMapperHelper, Class<T> entityClass) {
        this.specBuilder = Objects.requireNonNull(specBuilder);
        this.dtoMapperHelper = Objects.requireNonNull(dtoMapperHelper);
        this.entityClass = Objects.requireNonNull(entityClass);
    }

    protected abstract QueryRequest initFiltersAndSorts();

    protected record PreparedQueryContext<U>(QueryRequest safeRequest, QueryRequest clientOnlyRequest,
            QueryRequest internalRequest,
            boolean isDtoProjection, Class<U> rootEntity, int page, int size) {
    }

    protected PreparedQueryContext<T> prepare(QueryRequest request, Class<?> dtoOrEntity) {
        QueryRequest safeRequest = request != null ? request : QueryRequest.of(null);
        PageRequest pageReq = safeRequest.page();
        int page = Math.max(0, pageReq.page());
        int size = pageReq.size();

        boolean isDtoProjection = dtoOrEntity != null
                && dtoOrEntity.isAnnotationPresent(DtoMapper.class);

        @SuppressWarnings("unchecked")
        Class<T> rootEntity = isDtoProjection
                ? (Class<T>) specBuilder.resolveEntityClass(dtoOrEntity)
                : entityClass;

        QueryRequest internalRequest = initFiltersAndSorts();
        QueryRequest clientOnlyRequest = safeRequest.filter() == null
                ? null
                : new QueryRequest(safeRequest.filter(), List.of(), PageRequest.firstPage());

        return new PreparedQueryContext<>(
                safeRequest,
                clientOnlyRequest,
                internalRequest,
                isDtoProjection,
                rootEntity,
                page,
                size);
    }

    protected void applyWhere(CriteriaQuery<?> cq,
            SpecificationBuilder.CriteriaContext<T> ctx,
            Function<SpecificationBuilder.CriteriaContext<T>, Predicate> clientPred,
            Function<SpecificationBuilder.CriteriaContext<T>, Predicate> internalPred) {

        List<Predicate> preds = new ArrayList<>(2);
        if (clientPred != null) {
            preds.add(clientPred.apply(ctx));
        }
        if (internalPred != null) {
            preds.add(internalPred.apply(ctx));
        }
        if (!preds.isEmpty()) {
            cq.where(preds.toArray(Predicate[]::new));
        }
    }

    protected void applySort(CriteriaQuery<?> cq, Root<T> root, CriteriaBuilder cb, List<SortRequest> sorts,
            Class<?> dtoOrEntity, JoinContext joinCtx) {

        if (sorts == null || sorts.isEmpty()) {
            return;
        }

        List<Order> orders = sorts.stream()
                .filter(s -> s.field() != null && !s.field().isBlank())
                .map(s -> {
                    String entityPath = dtoOrEntity != null
                            ? specBuilder.resolveSortPath(s.field(), dtoOrEntity)
                            : s.field();
                    Path<?> path = specBuilder.resolvePathWithJoin(root, entityPath, joinCtx);
                    return s.direction() == SortDirection.DESC
                            ? cb.desc(path)
                            : cb.asc(path);
                })
                .collect(Collectors.toList());

        if (!orders.isEmpty()) {
            cq.orderBy(orders);
        }
    }

    protected Function<SpecificationBuilder.CriteriaContext<T>, Predicate> buildClientPredicate(QueryRequest clientRequest,
            Class<?> dtoOrEntity, JoinContext joinCtx) {

        if (clientRequest == null) {
            return null;
        }
        return specBuilder.buildPredicateWithJoin(clientRequest, dtoOrEntity, joinCtx);
    }

    protected Function<SpecificationBuilder.CriteriaContext<T>, Predicate> buildInternalPredicate(QueryRequest internalRequest,
            JoinContext joinCtx) {

        if (internalRequest == null) {
            return null;
        }
        return specBuilder.buildPredicateWithJoin(internalRequest, null, joinCtx);
    }
}