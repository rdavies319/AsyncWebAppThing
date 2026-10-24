package com.rdavies.arena;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A real Postgres for tests, so advisory locks and row locks behave as in production. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Same image as docker-compose.yml
        return new PostgreSQLContainer("postgres:15-alpine");
    }
}
