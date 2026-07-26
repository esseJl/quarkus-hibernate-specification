package io.quarkiverse.hibernatespecification.hibernate.specification.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.ComparisonOperator;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterGroup;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterPredicate;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.LogicalOperator;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.MultiValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.PageRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.QueryRequestBuilder;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.RangeValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SingleValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SortRequest;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.reactive.ReactiveSpecificationExecutor;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
public class ReactiveSpecificationExecutorIT {

    @Inject
    ReactiveSpecificationExecutor executor;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    private Author tolkien;
    private Author rowling;
    private Book lotr;
    private Book hobbit;
    private Book harry;

    @BeforeEach
    void setUp() {
        sessionFactory.withTransaction((session, tx) -> session
                .createQuery("delete from Book").executeUpdate()
                .call(() -> session.createQuery("delete from Author").executeUpdate())
                .call(() -> {
                    tolkien = Author.of("J.R.R. Tolkien");
                    rowling = Author.of("J.K. Rowling");
                    return session.persist(tolkien)
                            .call(() -> session.persist(rowling));
                })
                .call(() -> {
                    lotr = Book.of("The Lord of the Rings", "Fantasy", 29.99);
                    lotr.setAuthor(tolkien);

                    hobbit = Book.of("The Hobbit", "Fantasy", 19.99);
                    hobbit.setAuthor(tolkien);

                    harry = Book.of("Harry Potter", "Fantasy", 24.50);
                    harry.setAuthor(rowling);

                    Book orphan = Book.of("Orphan Book", "Mystery", 9.99);

                    return session.persist(lotr)
                            .call(() -> session.persist(hobbit))
                            .call(() -> session.persist(harry))
                            .call(() -> session.persist(orphan));
                }))
                .await().indefinitely();
    }

    private List<Book> find(QueryRequest request) {
        return executor.find(Book.class, request).await().indefinitely();
    }

    private long count(QueryRequest request) {
        return executor.count(Book.class, request).await().indefinitely();
    }

    @Test
    void shouldFindByTitleContains() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.CONTAINS, new SingleValue("Lord")));

        List<Book> result = find(request);

        assertEquals(1, result.size());
        assertEquals("The Lord of the Rings", result.get(0).getTitle());
    }

    @Test
    void shouldFindByTitleNotContains() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.NOT_CONTAINS, new SingleValue("Harry")));

        List<Book> result = find(request);

        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(b -> b.getTitle().contains("Harry")));
    }

    @Test
    void shouldFindByTitleEq() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.EQ, new SingleValue("The Hobbit")));

        List<Book> result = find(request);

        assertEquals(1, result.size());
        assertEquals("The Hobbit", result.get(0).getTitle());
    }

    @Test
    void shouldFindByTitleNeq() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.NEQ, new SingleValue("The Hobbit")));

        List<Book> result = find(request);

        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(b -> "The Hobbit".equals(b.getTitle())));
    }

    @Test
    void shouldFindByTitleLike() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.LIKE, new SingleValue("%Hobbit%")));

        List<Book> result = find(request);

        assertEquals(1, result.size());
        assertEquals("The Hobbit", result.get(0).getTitle());
    }

    @Test
    void shouldFindByTitleNotLike() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.NOT_LIKE, new SingleValue("%Potter%")));

        List<Book> result = find(request);

        assertEquals(3, result.size());
    }

    @Test
    void shouldFindByTitleStartsWith() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.STARTS_WITH, new SingleValue("The ")));

        List<Book> result = find(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(b -> b.getTitle().startsWith("The ")));
    }

    @Test
    void shouldFindByTitleEndsWith() {
        var request = QueryRequest.of(
                new FilterPredicate("title", ComparisonOperator.ENDS_WITH, new SingleValue("Rings")));

        List<Book> result = find(request);

        assertEquals(1, result.size());
        assertEquals("The Lord of the Rings", result.get(0).getTitle());
    }

    @Test
    void shouldFindByPriceGt() {
        var request = QueryRequest.of(
                new FilterPredicate("price", ComparisonOperator.GT, new SingleValue(20.0)));

        List<Book> result = find(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(b -> b.getPrice() > 20.0));
    }

    @Test
    void shouldFindByPriceGte() {
        var request = QueryRequest.of(
                new FilterPredicate("price", ComparisonOperator.GTE, new SingleValue(24.50)));

        List<Book> result = find(request);

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindByPriceLt() {
        var request = QueryRequest.of(
                new FilterPredicate("price", ComparisonOperator.LT, new SingleValue(20.0)));

        List<Book> result = find(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(b -> b.getPrice() < 20.0));
    }

    @Test
    void shouldFindByPriceLte() {
        var request = QueryRequest.of(
                new FilterPredicate("price", ComparisonOperator.LTE, new SingleValue(19.99)));

        List<Book> result = find(request);

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindByPriceBetween() {
        var request = QueryRequest.of(
                new FilterPredicate("price", ComparisonOperator.BETWEEN, new RangeValue(15.0, 25.0)));

        List<Book> result = find(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(b -> b.getPrice() >= 15.0 && b.getPrice() <= 25.0));
    }

    @Test
    void shouldFindByTitleIn() {
        var request = QueryRequest.of(
                new FilterPredicate(
                        "title",
                        ComparisonOperator.IN,
                        new MultiValue(List.of("The Hobbit", "Harry Potter"))));

        List<Book> result = find(request);

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindByTitleNotIn() {
        var request = QueryRequest.of(
                new FilterPredicate(
                        "title",
                        ComparisonOperator.NOT_IN,
                        new MultiValue(List.of("The Hobbit", "Harry Potter"))));

        List<Book> result = find(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(
                b -> "The Hobbit".equals(b.getTitle()) || "Harry Potter".equals(b.getTitle())));
    }

    @Test
    void shouldFindByAuthorIsNull() {
        var request = QueryRequest.of(
                new FilterPredicate("author", ComparisonOperator.IS_NULL, null));

        List<Book> result = find(request);

        assertEquals(1, result.size());
        assertEquals("Orphan Book", result.get(0).getTitle());
    }

    @Test
    void shouldFindByAuthorIsNotNull() {
        var request = QueryRequest.of(
                new FilterPredicate("author", ComparisonOperator.IS_NOT_NULL, null));

        List<Book> result = find(request);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(b -> b.getAuthor() != null));
    }

    @Test
    void shouldSupportNestedPath() {
        var request = new QueryRequest(
                new FilterPredicate("author.name", ComparisonOperator.EQ,
                        new SingleValue("J.R.R. Tolkien")),
                List.of(SortRequest.asc("title")),
                new PageRequest(0, 10));

        List<Book> result = find(request);

        assertEquals(2, result.size());
        assertEquals("The Hobbit", result.get(0).getTitle());
        assertEquals("The Lord of the Rings", result.get(1).getTitle());
    }

    @Test
    void shouldSupportGroupAndOr() {
        var andGroup = new FilterGroup(
                LogicalOperator.AND,
                List.of(
                        new FilterPredicate("genre", ComparisonOperator.EQ,
                                new SingleValue("Fantasy")),
                        new FilterPredicate("price", ComparisonOperator.GT,
                                new SingleValue(20.0))));

        var orGroup = new FilterGroup(
                LogicalOperator.OR,
                List.of(
                        andGroup,
                        new FilterPredicate("title", ComparisonOperator.CONTAINS,
                                new SingleValue("Hobbit"))));

        List<Book> result = find(QueryRequest.of(orGroup));

        assertEquals(3, result.size());
    }

    @Test
    void shouldSupportAndGroup() {
        var group = new FilterGroup(
                LogicalOperator.AND,
                List.of(
                        new FilterPredicate("genre", ComparisonOperator.EQ,
                                new SingleValue("Fantasy")),
                        new FilterPredicate("author.name", ComparisonOperator.EQ,
                                new SingleValue("J.R.R. Tolkien"))));

        List<Book> result = find(QueryRequest.of(group));

        assertEquals(2, result.size());
    }

    @Test
    void shouldSupportOrGroup() {
        var group = new FilterGroup(
                LogicalOperator.OR,
                List.of(
                        new FilterPredicate("title", ComparisonOperator.EQ,
                                new SingleValue("The Hobbit")),
                        new FilterPredicate("title", ComparisonOperator.EQ,
                                new SingleValue("Harry Potter"))));

        List<Book> result = find(QueryRequest.of(group));

        assertEquals(2, result.size());
    }

    @Test
    void shouldSupportNotGroup() {
        var notGroup = new FilterGroup(
                LogicalOperator.NOT,
                List.of(new FilterPredicate("title", ComparisonOperator.CONTAINS,
                        new SingleValue("Lord"))));

        List<Book> result = find(QueryRequest.of(notGroup));

        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(b -> b.getTitle().contains("Lord")));
    }

    @Test
    void shouldSortAscendingByTitle() {
        var request = new QueryRequestBuilder()
                .filter(new FilterPredicate("genre", ComparisonOperator.EQ,
                        new SingleValue("Fantasy")))
                .sortAsc("title")
                .page(0, 10)
                .build();

        List<Book> result = find(request);

        assertEquals(3, result.size());
        assertEquals("Harry Potter", result.get(0).getTitle());
        assertEquals("The Hobbit", result.get(1).getTitle());
        assertEquals("The Lord of the Rings", result.get(2).getTitle());
    }

    @Test
    void shouldSortDescendingByPrice() {
        var request = new QueryRequestBuilder()
                .filter(new FilterPredicate("genre", ComparisonOperator.EQ,
                        new SingleValue("Fantasy")))
                .sortDesc("price")
                .page(0, 10)
                .build();

        List<Book> result = find(request);

        assertEquals(3, result.size());
        assertEquals(29.99, result.get(0).getPrice());
        assertEquals(24.50, result.get(1).getPrice());
        assertEquals(19.99, result.get(2).getPrice());
    }

    @Test
    void shouldSupportPaging() {
        var page0 = new QueryRequestBuilder()
                .filter(new FilterPredicate("genre", ComparisonOperator.EQ,
                        new SingleValue("Fantasy")))
                .sortAsc("title")
                .page(0, 2)
                .build();

        var page1 = new QueryRequestBuilder()
                .filter(new FilterPredicate("genre", ComparisonOperator.EQ,
                        new SingleValue("Fantasy")))
                .sortAsc("title")
                .page(1, 2)
                .build();

        List<Book> first = find(page0);
        List<Book> second = find(page1);

        assertEquals(2, first.size());
        assertEquals(1, second.size());
        assertEquals("Harry Potter", first.get(0).getTitle());
        assertEquals("The Hobbit", first.get(1).getTitle());
        assertEquals("The Lord of the Rings", second.get(0).getTitle());
    }

    @Test
    void shouldUseFirstPageDefaults() {
        var request = QueryRequest.of(
                new FilterPredicate("genre", ComparisonOperator.EQ,
                        new SingleValue("Fantasy")));

        List<Book> result = find(request);

        assertEquals(3, result.size());
        assertEquals(0, request.page().page());
        assertEquals(20, request.page().size());
    }

    @Test
    void shouldCountWithoutFilter() {
        long total = count(new QueryRequest(null, List.of(), PageRequest.firstPage()));

        assertEquals(4, total);
    }

    @Test
    void shouldCountWithFilter() {
        var request = QueryRequest.of(
                new FilterPredicate("author.name", ComparisonOperator.EQ,
                        new SingleValue("J.R.R. Tolkien")));

        assertEquals(2, count(request));
    }

    @Test
    void shouldCountWithAndGroup() {
        var group = new FilterGroup(
                LogicalOperator.AND,
                List.of(
                        new FilterPredicate("genre", ComparisonOperator.EQ,
                                new SingleValue("Fantasy")),
                        new FilterPredicate("price", ComparisonOperator.GTE,
                                new SingleValue(24.50))));

        assertEquals(2, count(QueryRequest.of(group)));
    }

    @Test
    void shouldRejectBlankField() {
        assertThrows(IllegalArgumentException.class,
                () -> new FilterPredicate("  ", ComparisonOperator.EQ,
                        new SingleValue("x")));
    }

    @Test
    void shouldRejectMissingValueWhenRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new FilterPredicate("title", ComparisonOperator.EQ, null));
    }

    @Test
    void shouldRejectValueForIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new FilterPredicate("author", ComparisonOperator.IS_NULL,
                        new SingleValue("x")));
    }

    @Test
    void shouldRejectEmptyFilterGroup() {
        assertThrows(IllegalArgumentException.class,
                () -> new FilterGroup(LogicalOperator.AND, List.of()));
    }

    @Test
    void shouldRejectNotGroupWithMultipleChildren() {
        assertThrows(IllegalArgumentException.class,
                () -> new FilterGroup(
                        LogicalOperator.NOT,
                        List.of(
                                new FilterPredicate("title", ComparisonOperator.EQ,
                                        new SingleValue("a")),
                                new FilterPredicate("title", ComparisonOperator.EQ,
                                        new SingleValue("b")))));
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 201));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(-1, 10));
    }

    @Test
    void shouldComputePageOffset() {
        assertEquals(0, new PageRequest(0, 10).offset());
        assertEquals(20, new PageRequest(2, 10).offset());
    }
}
