package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import org.hibernate.Session;
import org.hibernate.query.Order;
import org.hibernate.query.range.Range;
import org.hibernate.query.restriction.Path;
import org.hibernate.query.restriction.Restriction;
import org.hibernate.query.specification.SelectionSpecification;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.ComparisonOperator;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterGroup;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterNode;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterPredicate;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.MultiValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.RangeValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SingleValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SortDirection;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SortRequest;

public class SelectionSpecificationEngine implements SpecificationEngine {

    @Inject
    public SelectionSpecificationEngine() {
    }

    @Override
    public QueryEngine engine() {
        return QueryEngine.SELECTION_SPECIFICATION;
    }

    @Override
    public <T> List<T> find(EntityManager em, Class<T> entityClass, QueryRequest request) {
        Session session = em.unwrap(Session.class);
        SelectionSpecification<T> spec = buildSpecification(em.getMetamodel(), entityClass, request);

        var query = spec.createQuery(session);
        if (request.page() != null) {
            query.setFirstResult(request.page().offset());
            query.setMaxResults(request.page().size());
        }
        return query.getResultList();
    }

    @Override
    public <T> long count(EntityManager em, Class<T> entityClass, QueryRequest request) {
        // createCountProjection() exists only from Hibernate 7.2+
        // Portable approach: Criteria count with the same Restriction applied via toPredicate
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        cq.select(cb.count(root));

        if (request.filter() != null) {
            Restriction<? super T> restriction = toRestriction(em.getMetamodel(), entityClass, request.filter());
            if (restriction != null) {
                cq.where(restriction.toPredicate(root, cb));
            }
        }
        return em.createQuery(cq).getSingleResult();
    }

    // -------------------------------------------------------------------------

    private <T> SelectionSpecification<T> buildSpecification(
            Metamodel metamodel, Class<T> entityClass, QueryRequest request) {

        SelectionSpecification<T> spec = SelectionSpecification.create(entityClass);

        if (request.filter() != null) {
            Restriction<? super T> restriction = toRestriction(metamodel, entityClass, request.filter());
            if (restriction != null) {
                spec = spec.restrict(restriction);
            }
        }

        if (request.sort() != null && !request.sort().isEmpty()) {
            List<Order<? super T>> orders = new ArrayList<>(request.sort().size());
            for (SortRequest s : request.sort()) {
                orders.add(toOrder(entityClass, s));
            }
            spec = spec.resort(orders);
        }
        return spec;
    }

    private <T> Restriction<? super T> toRestriction(
            Metamodel metamodel, Class<T> rootClass, FilterNode node) {
        return switch (node.type()) {
            case PREDICATE -> toPredicateRestriction(metamodel, rootClass, (FilterPredicate) node);
            case GROUP -> toGroupRestriction(metamodel, rootClass, (FilterGroup) node);
        };
    }

    private <T> Restriction<? super T> toGroupRestriction(
            Metamodel metamodel, Class<T> rootClass, FilterGroup group) {

        List<Restriction<? super T>> children = new ArrayList<>(group.children().size());
        for (FilterNode child : group.children()) {
            Restriction<? super T> r = toRestriction(metamodel, rootClass, child);
            if (r != null) {
                children.add(r);
            }
        }
        if (children.isEmpty()) {
            return null;
        }

        return switch (group.operator()) {
            case AND -> Restriction.all(children);
            case OR -> Restriction.any(children);
            case NOT -> {
                if (children.size() != 1) {
                    throw new IllegalStateException("NOT requires exactly one child restriction");
                }
                yield children.get(0).negated();
            }
        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> Restriction<? super T> toPredicateRestriction(
            Metamodel metamodel, Class<T> rootClass, FilterPredicate filter) {

        Path path = resolvePath(metamodel, rootClass, filter.field());
        Class<?> javaType = path.getType();
        ComparisonOperator op = filter.operator();
        FilterValue value = filter.value();

        return switch (op) {
            case EQ -> path.equalTo(convertSingle(value, javaType));

            case NEQ -> path.notEqualTo(convertSingle(value, javaType));

            case GT -> path.restrict(Range.greaterThan((Comparable) convertSingle(value, javaType)));

            case GTE -> path.restrict(Range.greaterThanOrEqualTo((Comparable) convertSingle(value, javaType)));

            case LT -> path.restrict(Range.lessThan((Comparable) convertSingle(value, javaType)));

            case LTE -> path.restrict(Range.lessThanOrEqualTo((Comparable) convertSingle(value, javaType)));

            case LIKE -> path.restrict(Range.pattern(String.valueOf(convertSingle(value, javaType))));

            case NOT_LIKE -> path.restrict(Range.pattern(String.valueOf(convertSingle(value, javaType)))).negated();

            case CONTAINS -> path.restrict(Range.containing(String.valueOf(convertSingle(value, javaType)), false));

            case NOT_CONTAINS -> path.restrict(Range.containing(String.valueOf(convertSingle(value, javaType)), false))
                    .negated();

            case STARTS_WITH -> path.restrict(Range.prefix(String.valueOf(convertSingle(value, javaType))));

            case ENDS_WITH -> path.restrict(Range.suffix(String.valueOf(convertSingle(value, javaType))));

            case IN -> path.in(List.copyOf(convertMulti(value, javaType)));

            case NOT_IN -> path.notIn(List.copyOf(convertMulti(value, javaType)));

            case BETWEEN -> {
                RangeValue range = (RangeValue) value;
                yield path.restrict(Range.closed(
                        (Comparable) convertRaw(range.lower(), javaType),
                        (Comparable) convertRaw(range.upper(), javaType)));
            }

            case IS_NULL -> path.notNull().negated();

            case IS_NOT_NULL -> path.notNull();
        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Path resolvePath(Metamodel metamodel, Class<?> rootClass, String fieldPath) {
        Objects.requireNonNull(fieldPath, "field path must not be null");
        if (fieldPath.isBlank()) {
            throw new IllegalArgumentException("field path must not be blank");
        }

        String[] parts = fieldPath.split("\\.");
        Path path = Path.from(rootClass);
        ManagedType<?> currentType = metamodel.managedType(rootClass);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            SingularAttribute<?, ?> attr;
            try {
                attr = currentType.getSingularAttribute(part);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Unknown attribute '" + part + "' on " + currentType.getJavaType().getName()
                                + " while resolving path '" + fieldPath + "'",
                        ex);
            }

            Class<?> javaType = attr.getJavaType();
            path = path.to(part, javaType);

            if (i < parts.length - 1) {
                Type<?> attrType = attr.getType();
                if (!(attrType instanceof ManagedType<?> mt)) {
                    throw new IllegalArgumentException(
                            "Cannot navigate into non-managed attribute '" + part
                                    + "' in path '" + fieldPath + "'");
                }
                currentType = mt;
            }
        }
        return path;
    }

    private <T> Order<? super T> toOrder(Class<T> rootClass, SortRequest sort) {
        // Order.asc/desc(Class, String) — supports nested names in recent Hibernate 7.x
        // For simple attributes this is the portable overload without SingularAttribute
        return sort.direction() == SortDirection.ASC
                ? Order.asc(rootClass, sort.field())
                : Order.desc(rootClass, sort.field());
    }

    private Object convertSingle(FilterValue value, Class<?> targetType) {
        if (!(value instanceof SingleValue s)) {
            throw new IllegalArgumentException("Operator expects SingleValue but got " + value.type());
        }
        return convertRaw(s.value(), targetType);
    }

    @SuppressWarnings("unchecked")
    private List convertMulti(FilterValue value, Class<?> targetType) {
        if (!(value instanceof MultiValue m)) {
            throw new IllegalArgumentException("Operator expects MultiValue but got " + value.type());
        }
        return m.values().stream()
                .map(v -> convertRaw(v, targetType))
                .toList();
    }

    private Object convertRaw(Object raw, Class<?> target) {
        if (raw == null) {
            return null;
        }
        if (target.isInstance(raw)) {
            return raw;
        }
        String s = String.valueOf(raw).trim();
        if (target == String.class)
            return s;
        if (target == Integer.class || target == int.class)
            return Integer.valueOf(s);
        if (target == Long.class || target == long.class)
            return Long.valueOf(s);
        if (target == Double.class || target == double.class)
            return Double.valueOf(s);
        if (target == Float.class || target == float.class)
            return Float.valueOf(s);
        if (target == Boolean.class || target == boolean.class)
            return Boolean.valueOf(s);
        if (target == java.math.BigDecimal.class)
            return new java.math.BigDecimal(s);
        if (target == java.util.UUID.class)
            return java.util.UUID.fromString(s);
        if (target == java.time.LocalDate.class)
            return java.time.LocalDate.parse(s);
        if (target == java.time.LocalDateTime.class)
            return java.time.LocalDateTime.parse(s);
        if (target == java.time.Instant.class)
            return java.time.Instant.parse(s);
        if (target == java.time.OffsetDateTime.class)
            return java.time.OffsetDateTime.parse(s);
        if (Enum.class.isAssignableFrom(target)) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object e = Enum.valueOf((Class<? extends Enum>) target, s);
            return e;
        }
        return raw;
    }
}