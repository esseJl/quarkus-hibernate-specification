package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.ComparisonOperator;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterGroup;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterNode;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterPredicate;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.MultiValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.RangeValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SingleValue;

public class CriteriaPredicateFactory {

    private final FilterValueConverter converter;

    @Inject
    public CriteriaPredicateFactory(FilterValueConverter converter) {
        this.converter = Objects.requireNonNull(converter);
    }

    public Predicate build(CriteriaBuilder cb, From<?, ?> root, FilterNode node) {
        if (node == null) {
            return cb.conjunction();
        }
        return switch (node.type()) {
            case PREDICATE -> toPredicate(cb, root, (FilterPredicate) node);
            case GROUP -> toGroup(cb, root, (FilterGroup) node);
        };
    }

    private Predicate toGroup(CriteriaBuilder cb, From<?, ?> root, FilterGroup group) {
        List<Predicate> children = group.children().stream()
                .map(child -> build(cb, root, child))
                .toList();

        return switch (group.operator()) {
            case AND -> cb.and(children.toArray(Predicate[]::new));
            case OR -> cb.or(children.toArray(Predicate[]::new));
            case NOT -> cb.not(children.get(0));
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Predicate toPredicate(CriteriaBuilder cb, From<?, ?> root, FilterPredicate filter) {
        Path path = CriteriaPathResolver.resolve(root, filter.field());
        ComparisonOperator op = filter.operator();
        FilterValue value = filter.value();

        BiFunction<Path, Object, Predicate> single = (p, v) -> switch (op) {
            case EQ -> cb.equal(p, v);
            case NEQ -> cb.notEqual(p, v);
            case GT -> cb.greaterThan(p, (Comparable) v);
            case GTE -> cb.greaterThanOrEqualTo(p, (Comparable) v);
            case LT -> cb.lessThan(p, (Comparable) v);
            case LTE -> cb.lessThanOrEqualTo(p, (Comparable) v);
            case LIKE -> cb.like(p.as(String.class), String.valueOf(v));
            case NOT_LIKE -> cb.notLike(p.as(String.class), String.valueOf(v));
            case CONTAINS -> cb.like(p.as(String.class), "%" + v + "%");
            case NOT_CONTAINS -> cb.notLike(p.as(String.class), "%" + v + "%");
            case STARTS_WITH -> cb.like(p.as(String.class), v + "%");
            case ENDS_WITH -> cb.like(p.as(String.class), "%" + v);
            default -> throw new IllegalStateException("Unexpected single-value operator: " + op);
        };

        return switch (op) {
            case EQ, NEQ, GT, GTE, LT, LTE, LIKE, NOT_LIKE,
                    CONTAINS, NOT_CONTAINS, STARTS_WITH, ENDS_WITH ->
                single.apply(path, convertSingle(value, path));

            case IN -> path.in(convertMulti(value, path));
            case NOT_IN -> cb.not(path.in(convertMulti(value, path)));

            case BETWEEN -> {
                RangeValue range = (RangeValue) value;
                yield cb.between(path,
                        (Comparable) converter.convert(range.lower(), path),
                        (Comparable) converter.convert(range.upper(), path));
            }

            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
        };
    }

    private Object convertSingle(FilterValue value, Path<?> path) {
        return converter.convert(single(value), path);
    }

    private Collection<?> convertMulti(FilterValue value, Path<?> path) {
        return multi(value).stream()
                .map(v -> converter.convert(v, path))
                .toList();
    }

    private static Object single(FilterValue value) {
        if (!(value instanceof SingleValue s)) {
            throw new IllegalArgumentException("Expected SingleValue but got " + value.type());
        }
        return s.value();
    }

    private static Collection<?> multi(FilterValue value) {
        if (!(value instanceof MultiValue m)) {
            throw new IllegalArgumentException("Expected MultiValue but got " + value.type());
        }
        return m.values();
    }
}
