package com.rdavies.api;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drives the game to a known state, for any {@link GameTest} class to {@code @Autowired}. The arena
 * verbs (startFight, nextExchange, finishFight) arrive with the fight engine.
 */
public class TestGame {
    private final JdbcTemplate jdbc;

    TestGame(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The ledger invariant: balance = SUM(ledger.delta). */
    void assertLedgerMatchesWallet(long userId) {
        assertEquals(jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", Long.class, userId),
                jdbc.queryForObject("SELECT SUM(delta) FROM ledger WHERE user_id = ?", Long.class, userId));
    }
}
