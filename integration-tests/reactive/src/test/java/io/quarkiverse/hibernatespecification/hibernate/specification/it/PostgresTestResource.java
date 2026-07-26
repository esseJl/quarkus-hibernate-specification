package io.quarkiverse.hibernatespecification.hibernate.specification.it;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("test")
            .withUsername("user")
            .withPassword("pass");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();

        Map<String, String> config = new HashMap<>();

        return config;
    }

    @Override
    public void stop() {
        POSTGRES.stop();
    }
}