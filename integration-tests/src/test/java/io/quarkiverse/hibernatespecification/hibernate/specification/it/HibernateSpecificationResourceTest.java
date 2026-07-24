package io.quarkiverse.hibernatespecification.hibernate.specification.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HibernateSpecificationResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/hibernate-specification")
                .then()
                .statusCode(200)
                .body(is("Hello hibernate-specification"));
    }
}
