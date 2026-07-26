package io.quarkiverse.hibernatespecification.hibernate.specification.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

public class HibernateSpecificationDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Author.class, Book.class)
                    .addAsResource("application.properties"));

    @Test
    public void writeYourOwnDevModeTest() {
        Assertions.assertTrue(true, "Add dev mode assertions to " + getClass().getName());
    }
}
