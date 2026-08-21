package com.etka.veridoc;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Supplies a PostgreSQL container to tests that need a database.
 *
 * <p>{@code @ServiceConnection} wires the container's generated URL, username
 * and password into Spring automatically, so no test-specific datasource
 * configuration is needed and the properties in application.properties are
 * overridden for the duration of the run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DatabaseTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}