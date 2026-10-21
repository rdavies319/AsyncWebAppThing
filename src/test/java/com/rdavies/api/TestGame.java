package com.rdavies.api;

import com.rdavies.api.fight.FightEngine;
import com.rdavies.api.fight.FightService;
import com.rdavies.api.fight.TestFights;
import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;
import com.rdavies.api.fight.records.CurrentFights;
import com.rdavies.api.fight.records.FightResult;
import com.rdavies.api.fight.records.LiveFight;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drives the arena to a known state, for any {@link GameTest} class to {@code @Autowired}. Each verb
 * moves the test clock and ticks the real engine, so fights announce, start, fight and settle as in
 * production.
 */
public class TestGame {
    /** Half a minute past midnight, so bootstrap announces the first fight for 01:00. */
    public static final Instant BOOT = Instant.parse("2026-01-01T00:00:30Z");

    private final FightEngine engine;
    private final FightService fights;
    private final TestFights testFights;
    private final MutableClock clock;
    private final JdbcTemplate jdbc;

    TestGame(FightEngine engine, FightService fights, TestFights testFights, MutableClock clock, JdbcTemplate jdbc) {
        this.testFights = testFights;
        this.engine = engine;
        this.fights = fights;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /** Sets the clock to {@code when} and runs one engine tick. Returns what's on now. */
    public CurrentFights tickAt(Instant when) {
        clock.now = when;
        engine.tick();
        return fights.current();
    }

    /** Ticks at {@link #BOOT}, which announces the first fight when none exists. */
    public CurrentFights boot() {
        return tickAt(BOOT);
    }

    /** Starts the announced fight at its slot, bootstrapping one first if there is none. Returns it live. */
    public LiveFight startFight() {
        CurrentFights now = fights.current();
        if (now.announced() == null) {
            now = boot();
        }
        assertNotNull(now.announced(), "a fight to start");
        LiveFight live = tickAt(now.announced().startsAt()).live();
        assertNotNull(live, "the announced fight went live");
        return live;
    }

    /** Runs the live fight's next exchange at its due time. Returns what's on afterwards. */
    public CurrentFights nextExchange() {
        LiveFight live = fights.current().live();
        assertNotNull(live, "a live fight");
        return tickAt(live.nextExchangeAt());
    }

    /** Runs exchanges until the live fight settles, starting one first if none is live. Returns the result. */
    public FightResult finishFight() {
        LiveFight live = fights.current().live();
        if (live == null) {
            live = startFight();
        }
        while (fights.current().live() != null) {
            nextExchange();
        }
        return fights.result(live.fightId());
    }

    /**
     * Makes the announced fight end as wanted (bootstrapping one first if there is none), by giving it
     * a seed found by search. The engine still plays the fight out from that seed.
     */
    public void plantFight(Winner winner, EndedBy endedBy) {
        if (fights.current().announced() == null) {
            boot();
        }
        testFights.plant(winner, endedBy);
    }

    /** The ledger invariant: balance = SUM(ledger.delta). */
    void assertLedgerMatchesWallet(long userId) {
        assertEquals(jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", Long.class, userId),
                jdbc.queryForObject("SELECT SUM(delta) FROM ledger WHERE user_id = ?", Long.class, userId));
    }
}
