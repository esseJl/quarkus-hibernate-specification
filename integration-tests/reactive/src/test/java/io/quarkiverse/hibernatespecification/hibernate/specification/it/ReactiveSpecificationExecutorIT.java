package io.quarkiverse.hibernatespecification.hibernate.specification.it;

import jakarta.inject.Inject;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
public class ReactiveSpecificationExecutorIT {

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

}
