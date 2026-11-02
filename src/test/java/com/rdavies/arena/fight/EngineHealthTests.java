package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /health against the real engine, with no login. A yearly tick gives the check a cadence to
 * measure while keeping the scheduler quiet, so only these tests tick. Not @Transactional, because
 * a tick counts only once it commits.
 */
@GameTest
@TestPropertySource(properties = "game.engine.cron=0 0 0 1 1 ?")
class EngineHealthTests {
    private static final Instant TICKED = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired FightEngine engine;
    @Autowired MutableClock clock;
    @Autowired TestFights testFights;
    @Autowired TransactionTemplate transaction;
    @Autowired DataSource dataSource;

    @BeforeEach
    void tickOnce() {
        testFights.deleteAll();
        clock.set(TICKED);
        assertTrue(engine.tick());
    }

    @Test
    void aCommittedTickIsHealthy() throws Exception {
        health().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.last_tick_at").value("2026-06-01T00:00:00Z"));
    }

    @Test
    void itStaysHealthyUntilThreeScheduledTicksHavePassed() throws Exception {
        clock.set(Instant.parse("2029-01-01T00:00:00Z")); // the third tick since is only just due

        health().andExpect(status().isOk());
    }

    @Test
    void itReportsStoppedOnceThreeScheduledTicksHavePassed() throws Exception {
        clock.set(Instant.parse("2029-01-01T00:00:01Z"));

        health().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("stopped"))
                .andExpect(jsonPath("$.last_tick_at").value("2026-06-01T00:00:00Z"));
    }

    @Test
    void aRolledBackTickDoesNotCount() throws Exception {
        clock.set(Instant.parse("2027-06-01T00:00:00Z"));

        transaction.executeWithoutResult(tx -> {
            assertTrue(engine.tick());
            tx.setRollbackOnly();
        });

        health().andExpect(jsonPath("$.last_tick_at").value("2026-06-01T00:00:00Z"));
    }

    @Test
    void aSkippedTickChangesNothing() throws Exception {
        clock.set(Instant.parse("2027-06-01T00:00:00Z"));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, FightEngine.LOCK_KEY);
                lock.execute();
            }
            assertFalse(engine.tick());
            other.rollback();
        }

        health().andExpect(status().isOk())
                .andExpect(jsonPath("$.last_tick_at").value("2026-06-01T00:00:00Z"));
    }

    private ResultActions health() throws Exception {
        return mockMvc.perform(get("/health").accept(MediaType.APPLICATION_JSON));
    }
}
