package com.rdavies.arena.gladiator;

import com.rdavies.arena.ArenaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.NestedExceptionUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/** The whole app, booted as in production against its own empty database. */
class DevRosterStartupTests {

    @Test
    void theAppWontStartOnTheDevRosterOutsideTheDevProfile() {
        // Same image as docker-compose.yml
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")) {
            postgres.start();

            Exception failure = assertThrows(Exception.class, () -> SpringApplication.run(ArenaApplication.class,
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--server.port=0",
                    "--game.roster-file=classpath:roster-dev.json"));

            String reason = NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
            assertTrue(reason.contains("GAME_ROSTER_FILE"), reason);
        }
    }
}
