package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria;

import java.util.List;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.PageRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SortDirection;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SortRequest;

public final class SpecificationQuerySupport {

    private SpecificationQuerySupport() {
    }

    public static <T> void applySort(CriteriaBuilder cb, CriteriaQuery<T> cq,
            Root<T> root, List<SortRequest> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return;
        }

        List<Order> orders = sorts.stream()
                .map(sort -> {
                    var path = CriteriaPathResolver.resolve(root, sort.field());
                    return sort.direction() == SortDirection.ASC
                            ? cb.asc(path)
                            : cb.desc(path);
                })
                .toList();

        cq.orderBy(orders);
    }

    public static <T> void applyPaging(TypedQuery<T> query, PageRequest page) {
        if (page == null) {
            return;
        }
        query.setFirstResult(page.offset());
        query.setMaxResults(page.size());
    }

    public static <T> void applySortAndWhere(CriteriaBuilder cb, CriteriaQuery<T> cq,
            Root<T> root, QueryRequest request,
            CriteriaPredicateFactory predicates) {
        if (request.filter() != null) {
            cq.where(predicates.build(cb, root, request.filter()));
        }
        applySort(cb, cq, root, request.sort());
    }
}
