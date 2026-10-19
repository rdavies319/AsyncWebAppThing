package com.rdavies.api;

import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundEngine;
import com.rdavies.api.round.RoundRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared game fixtures for tests. */
final class TestGame {
    /** Just after a minute boundary, as when the scheduled tick fires. */
    static final Instant ROUND_START = Instant.parse("2026-01-01T00:00:00.200Z");

    private TestGame() {}

    /** Ticks at ROUND_START, which bootstraps the first round, and returns it. */
    static Round openRound(RoundEngine engine, MutableClock clock, RoundRepository rounds) {
        clock.now = ROUND_START;
        engine.tick();
        return rounds.findOpenRound().orElseThrow();
    }

    /** A direction that can win against this round's roll (Higher, unless the roll is 100). */
    static PredictionType allowedDirection(Round round) {
        return round.rollValue() < 100 ? PredictionType.HIGHER : PredictionType.LOWER;
    }

    /** The ledger invariant: balance = SUM(ledger.delta). */
    static void assertLedgerMatchesWallet(JdbcTemplate jdbc, long userId) {
        assertEquals(jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", Long.class, userId),
                jdbc.queryForObject("SELECT SUM(delta) FROM ledger WHERE user_id = ?", Long.class, userId));
    }
}
