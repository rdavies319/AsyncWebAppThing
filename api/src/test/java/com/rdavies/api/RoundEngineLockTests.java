package com.rdavies.api;

import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundStatus;
import com.rdavies.api.round.RoundRepository;
import com.rdavies.api.round.RoundEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Not @Transactional, unlike GameFlowTests: each tick must commit and release its advisory lock,
 * otherwise the test transaction itself holds the lock and the "other instance" blocks forever.
 * Rows are committed, so they are deleted after each test.
 */
@GameTest
class RoundEngineLockTests {
    @Autowired TestGame game;
    @Autowired RoundRepository roundRepository;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void deleteCommittedRounds() {
        jdbc.update("DELETE FROM rounds");
    }

    @Test
    @Timeout(30)
    void tickSkipsWhileAnotherInstanceHoldsTheEngineLock() throws SQLException {
        Round round = game.open();
        Instant rollTime = TestGame.ROUND_START.plusSeconds(60);

        // A separate connection stands in for another instance mid-tick
        try (Connection other = dataSource.getConnection()) {
            advisoryLock(other, "pg_advisory_lock");
            try {
                game.tickAt(rollTime);
                assertEquals(RoundStatus.OPEN, roundRepository.findById(round.id()).orElseThrow().status());
                assertEquals(2, roundRepository.count()); // open and scheduled
            } finally {
                advisoryLock(other, "pg_advisory_unlock");
            }
        }

        game.tickAt(rollTime);
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(round.id()).orElseThrow().status());
    }

    private static void advisoryLock(Connection connection, String function) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + function + "(?)")) {
            statement.setLong(1, RoundEngine.ENGINE_LOCK_KEY);
            statement.execute();
        }
    }
}
