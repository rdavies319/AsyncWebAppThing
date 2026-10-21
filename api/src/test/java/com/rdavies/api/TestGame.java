package com.rdavies.api;

import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.ProvablyFair;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundEngine;
import com.rdavies.api.round.RoundRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drives the game to a known state, for any {@link GameTest} class to {@code @Autowired}. Moves the
 * test clock and ticks the real engine, so rounds still open, roll and settle as in production.
 */
public class TestGame {
    /** Just after a minute boundary, as when the scheduled tick fires. */
    static final Instant ROUND_START = Instant.parse("2026-01-01T00:00:00.200Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoundEngine engine;
    private final RoundRepository rounds;
    private final MutableClock clock;
    private final JdbcTemplate jdbc;

    TestGame(RoundEngine engine, RoundRepository rounds, MutableClock clock, JdbcTemplate jdbc) {
        this.engine = engine;
        this.rounds = rounds;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /** Ticks at ROUND_START, which bootstraps the first round at 00:00:00, and returns it. */
    Round open() {
        return tickAt(ROUND_START);
    }

    /**
     * Opens the game on a round showing {@code roll}: bootstraps if needed, then rolls the next
     * round with that value. The returned round starts a minute after the one before it.
     */
    Round startFrom(int roll) {
        if (rounds.findOpenRound().isEmpty()) {
            open();
        }
        plantNextRoll(roll);
        Round round = rollNext();
        assertEquals(roll, round.rollValue());
        return round;
    }

    /** Ticks at the open round's roll time (just after, as the scheduler would), settling it. Returns the new open round. */
    Round rollNext() {
        return tickAt(current().rollTime().plusMillis(200));
    }

    /** Sets the clock to {@code when} and runs one engine tick. Returns the open round afterwards. */
    Round tickAt(Instant when) {
        clock.now = when;
        engine.tick();
        return current();
    }

    /**
     * Makes the scheduled round roll {@code wanted}, by replacing it with one whose seed rolls that
     * value (about 100 tries). Stands in for chance; the engine still derives the roll from the
     * committed seed.
     */
    void plantNextRoll(int wanted) {
        Round next = scheduled();
        String seed;
        do {
            seed = ProvablyFair.newSeed(RANDOM);
        } while (ProvablyFair.roll(seed, next.id()) != wanted);
        jdbc.update("DELETE FROM rounds WHERE id = ?", next.id());
        jdbc.update("INSERT INTO rounds (id, starts_at, seed_commit_hash, seed, status) VALUES (?, ?, ?, ?, 'SCHEDULED')",
                next.id(), Timestamp.from(next.startsAt()), ProvablyFair.commit(seed), seed);
    }

    Round current() {
        return rounds.findOpenRound().orElseThrow();
    }

    Round scheduled() {
        return rounds.findScheduledRound().orElseThrow();
    }

    /** The ledger invariant: balance = SUM(ledger.delta). */
    void assertLedgerMatchesWallet(long userId) {
        assertEquals(jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", Long.class, userId),
                jdbc.queryForObject("SELECT SUM(delta) FROM ledger WHERE user_id = ?", Long.class, userId));
    }

    /** A direction that can win against this round's roll (Higher, unless the roll is 100). */
    static PredictionType allowedDirection(Round round) {
        return round.rollValue() < 100 ? PredictionType.HIGHER : PredictionType.LOWER;
    }
}
