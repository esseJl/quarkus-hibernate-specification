package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.*;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.exception.SpecificationException;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;

@ApplicationScoped
public class SpecificationBuilder {

    @ConfigProperty(name = "quarkus.hibernate-specification.max-filter-depth", defaultValue = "32")
    private static int MAX_FILTER_DEPTH;

    private final FieldMetaRegistry fieldMetaRegistry;
    private final ValueConverter valueConverter;
    private final PathResolver pathResolver;

    private enum Comparison {
        GT,
        GE,
        LT,
        LE
    }

    @Inject
    public SpecificationBuilder(
            FieldMetaRegistry fieldMetaRegistry,
            ValueConverter valueConverter,
            PathResolver pathResolver) {
        this.fieldMetaRegistry = fieldMetaRegistry;
        this.valueConverter = valueConverter;
        this.pathResolver = pathResolver;
    }

    public <T> Function<CriteriaContext<T>, Predicate> buildPredicate(
            QueryRequest request,
            Class<?> dtoOrEntity) {

        return ctx -> {
            JoinContext joinCtx = new JoinContext();
            return this.<T> buildPredicateWithJoin(request, dtoOrEntity, joinCtx).apply(ctx);
        };
    }

    public <T> Function<CriteriaContext<T>, Predicate> buildPredicateWithJoin(
            QueryRequest request, Class<?> dtoOrEntity, JoinContext joinCtx) {

        Objects.requireNonNull(joinCtx, "joinCtx must not be null");

        if (request == null || request.filter() == null) {
            return ctx -> ctx.cb().conjunction();
        }

        final FilterNode rootNode = request.filter();

        return ctx -> {
            Predicate predicate = buildNode(rootNode, ctx.root(), ctx.cb(), dtoOrEntity, joinCtx, 0);

            if (joinCtx.hasCollectionJoin() && ctx.query() != null) {
                ctx.query().distinct(true);
            }

            return predicate != null ? predicate : ctx.cb().conjunction();
        };
    }

    public List<FieldMeta> getProjectionFieldMetas(Class<?> dtoOrEntity) {
        return fieldMetaRegistry.getProjectionFieldMetas(dtoOrEntity);
    }

    public <T> List<Selection<?>> buildProjectionSelectionsWithAlias(
            Root<T> root, Class<?> dtoOrEntity, JoinContext joinCtx) {

        List<FieldMeta> metas = fieldMetaRegistry.getProjectionFieldMetas(dtoOrEntity);
        List<Selection<?>> selections = new ArrayList<>(metas.size());
        for (FieldMeta m : metas) {
            Path<?> path = pathResolver.resolvePathWithJoin(root, m.entityPath(), joinCtx);
            String alias = sanitizeAlias(m.dtoName());
            selections.add(path.alias(alias));
        }
        return selections;
    }

    public String resolveSortPath(String clientField, Class<?> dtoOrEntity) {
        return pathResolver.resolveSortPath(clientField, dtoOrEntity);
    }

    public Path<?> resolvePathWithJoin(From<?, ?> from, String entityPath, JoinContext ctx) {
        return pathResolver.resolvePathWithJoin(from, entityPath, ctx);
    }

    public Class<?> resolveEntityClass(Class<?> dtoOrEntity) {
        return fieldMetaRegistry.resolveEntityClass(dtoOrEntity);
    }

    private Predicate buildNode(FilterNode node, Root<?> root, CriteriaBuilder cb,
            Class<?> dtoOrEntity, JoinContext joinCtx, int depth) {

        if (node == null) {
            return null;
        }
        if (depth > MAX_FILTER_DEPTH) {
            throw new SpecificationException(
                    "Filter tree exceeds maximum allowed depth of " + MAX_FILTER_DEPTH);
        }

        return switch (node.type()) {
            case PREDICATE -> buildSinglePredicate((FilterPredicate) node, root, cb, dtoOrEntity, joinCtx);
            case GROUP -> buildGroup((FilterGroup) node, root, cb, dtoOrEntity, joinCtx, depth + 1);
        };
    }

    private Predicate buildGroup(FilterGroup group, Root<?> root, CriteriaBuilder cb,
            Class<?> dtoOrEntity, JoinContext joinCtx, int depth) {

        List<FilterNode> children = group.children();
        if (children.isEmpty()) {
            return cb.conjunction();
        }

        LogicalOperator op = group.operator();

        if (op == LogicalOperator.NOT) {
            Predicate child = buildNode(children.get(0), root, cb, dtoOrEntity, joinCtx, depth);
            return child == null ? cb.conjunction() : cb.not(child);
        }

        List<Predicate> predicates = new ArrayList<>(children.size());
        for (FilterNode child : children) {
            Predicate p = buildNode(child, root, cb, dtoOrEntity, joinCtx, depth);
            if (p != null) {
                predicates.add(p);
            }
        }

        if (predicates.isEmpty()) {
            return cb.conjunction();
        }
        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        Predicate[] arr = predicates.toArray(new Predicate[0]);
        return (op == LogicalOperator.OR) ? cb.or(arr) : cb.and(arr);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Predicate buildSinglePredicate(
            FilterPredicate predicate,
            Root<?> root,
            CriteriaBuilder cb,
            Class<?> dtoOrEntity,
            JoinContext joinCtx) {

        try {
            Path<?> path = pathResolver.resolvePath(root, predicate.field(), dtoOrEntity, joinCtx);
            Class<?> javaType = path.getJavaType();
            ComparisonOperator operator = predicate.operator();
            FilterValue filterValue = predicate.value();

            if (operator == ComparisonOperator.IS_NULL) {
                return cb.isNull(path);
            }
            if (operator == ComparisonOperator.IS_NOT_NULL) {
                return cb.isNotNull(path);
            }

            if (filterValue == null) {
                throw new IllegalArgumentException("value is required for operator " + operator);
            }

            return switch (operator) {
                case EQ -> cb.equal(path, valueConverter.convertSingle(filterValue, javaType));
                case NEQ -> cb.notEqual(path, valueConverter.convertSingle(filterValue, javaType));

                case GT -> buildComparable(cb, path,
                        valueConverter.convertSingle(filterValue, javaType), Comparison.GT);
                case GTE -> buildComparable(cb, path,
                        valueConverter.convertSingle(filterValue, javaType), Comparison.GE);
                case LT -> buildComparable(cb, path,
                        valueConverter.convertSingle(filterValue, javaType), Comparison.LT);
                case LTE -> buildComparable(cb, path,
                        valueConverter.convertSingle(filterValue, javaType), Comparison.LE);

                case LIKE -> stringLike(cb, path, javaType,
                        valueConverter.convertSingle(filterValue, String.class), false, false);
                case NOT_LIKE -> cb.not(stringLike(cb, path, javaType,
                        valueConverter.convertSingle(filterValue, String.class), false, false));

                case CONTAINS -> stringLike(cb, path, javaType,
                        valueConverter.convertSingle(filterValue, String.class), true, true);
                case NOT_CONTAINS -> cb.not(stringLike(cb, path, javaType,
                        valueConverter.convertSingle(filterValue, String.class), true, true));

                case STARTS_WITH -> stringLike(cb, path, javaType,
                        valueConverter.convertSingle(filterValue, String.class), false, true);
                case ENDS_WITH -> stringLike(cb, path, javaType,
                        valueConverter.convertSingle(filterValue, String.class), true, false);

                case IN -> path.in(valueConverter.convertMulti(filterValue, javaType));
                case NOT_IN -> cb.not(path.in(valueConverter.convertMulti(filterValue, javaType)));

                case BETWEEN -> {
                    RangeValue range = valueConverter.requireRange(filterValue);
                    Object lower = valueConverter.convertValue(range.lower(), javaType);
                    Object upper = valueConverter.convertValue(range.upper(), javaType);

                    if (lower == null && upper == null) {
                        yield cb.conjunction();
                    }
                    if (lower == null) {
                        yield buildComparable(cb, path, upper, Comparison.LE);
                    }
                    if (upper == null) {
                        yield buildComparable(cb, path, lower, Comparison.GE);
                    }
                    yield cb.between(
                            (Expression<? extends Comparable>) path,
                            (Comparable) lower,
                            (Comparable) upper);
                }

                default -> throw new UnsupportedOperationException("Operator not supported: " + operator);
            };
        } catch (SpecificationException | IllegalArgumentException re) {
            throw re;
        } catch (Exception e) {
            throw new SpecificationException(
                    "Invalid filter for field: '" + predicate.field()
                            + "' with operator " + predicate.operator()
                            + " - " + e.getMessage(),
                    e);
        }
    }

    private Predicate stringLike(
            CriteriaBuilder cb,
            Path<?> path,
            Class<?> javaType,
            Object value,
            boolean prefixWildcard,
            boolean suffixWildcard) {

        if (!String.class.isAssignableFrom(javaType)) {
            throw new IllegalArgumentException("String operator can only be applied to String fields");
        }

        String pattern = escapeLike(Objects.toString(value, ""));
        if (prefixWildcard) {
            pattern = "%" + pattern;
        }
        if (suffixWildcard) {
            pattern = pattern + "%";
        }
        return cb.like(cb.lower(path.as(String.class)), pattern.toLowerCase(), '\\');
    }

    private static String escapeLike(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Predicate buildComparable(CriteriaBuilder cb, Path<?> path, Object value, Comparison comp) {
        if (!(value instanceof Comparable c)) {
            throw new IllegalArgumentException(
                    "Value is not Comparable. Type: " + (value != null ? value.getClass().getName() : "null"));
        }
        Expression<? extends Comparable> expr = (Expression<? extends Comparable>) path;
        return switch (comp) {
            case GT -> cb.greaterThan(expr, c);
            case GE -> cb.greaterThanOrEqualTo(expr, c);
            case LT -> cb.lessThan(expr, c);
            case LE -> cb.lessThanOrEqualTo(expr, c);
        };
    }

    public static String sanitizeAlias(String dtoName) {
        return dtoName == null ? "" : dtoName.replace('.', '_');
    }

    public record CriteriaContext<T>(
            Root<T> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb) {
    }
}
