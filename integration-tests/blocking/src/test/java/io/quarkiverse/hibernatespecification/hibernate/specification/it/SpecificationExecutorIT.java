package io.quarkiverse.hibernatespecification.hibernate.specification.it;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SpecificationExecutorIT {

    @Inject
    EntityManager entityManager;

    private Author tolkien;
    private Author rowling;
    private Book lotr;
    private Book hobbit;
    private Book harry;

    @BeforeEach
    @Transactional
    void setUp() {
        entityManager.createQuery("delete from Book").executeUpdate();
        entityManager.createQuery("delete from Author").executeUpdate();

        tolkien = Author.of("J.R.R. Tolkien");
        entityManager.persist(tolkien);

        rowling = Author.of("J.K. Rowling");
        entityManager.persist(rowling);

        lotr = Book.of("The Lord of the Rings", "Fantasy", 29.99);
        lotr.setAuthor(tolkien);
        entityManager.persist(lotr);

        hobbit = Book.of("The Hobbit", "Fantasy", 19.99);
        hobbit.setAuthor(tolkien);
        entityManager.persist(hobbit);

        harry = Book.of("Harry Potter", "Fantasy", 24.50);
        harry.setAuthor(rowling);
        entityManager.persist(harry);

        // book without author for IS_NULL tests
        Book orphan = Book.of("Orphan Book", "Mystery", 9.99);
        entityManager.persist(orphan);
    }

    // ---------- find: basic string operators ----------

}
