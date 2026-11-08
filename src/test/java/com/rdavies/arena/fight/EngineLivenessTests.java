package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.fight.events.EngineTicked;
import com.rdavies.arena.fight.records.EngineHealth;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Before any tick has committed. A booted app has already run its startup tick, so these don't boot one. */
class EngineLivenessTests {
    private final MutableClock clock = new MutableClock();

    @Test
    void aFreshAppIsHealthyBeforeItsFirstTick() {
        clock.set(Instant.parse("2026-06-01T12:00:30Z"));

        EngineHealth health = liveness("0 * * * * *").health();

        assertEquals(EngineHealth.Status.OK, health.status());
        assertNull(health.lastTickAt());
    }

    @Test
    void anAppThatNeverTicksStopsBeingHealthy() {
        clock.set(Instant.parse("2026-06-01T12:00:30Z"));
        EngineLiveness liveness = liveness("0 * * * * *");

        clock.set(Instant.parse("2026-06-01T12:03:00Z"));
        assertEquals(EngineHealth.Status.OK, liveness.health().status());

        clock.set(Instant.parse("2026-06-01T12:03:01Z"));
        assertEquals(EngineHealth.Status.STOPPED, liveness.health().status());
    }

    @Test
    void theLimitFollowsTheSchedule() {
        clock.set(Instant.parse("2026-06-01T12:00:00Z"));
        EngineLiveness liveness = liveness("* * * * * *"); // the dev cadence

        clock.set(Instant.parse("2026-06-01T12:00:04Z"));
        assertEquals(EngineHealth.Status.STOPPED, liveness.health().status());
    }

    @Test
    void withTheScheduleOffAnEngineThatNeverTickedIsStopped() {
        assertEquals(EngineHealth.Status.STOPPED, liveness("-").health().status());
    }

    @Test
    void withTheScheduleOffAManualTickHasNoLimit() {
        EngineLiveness liveness = liveness("-");
        liveness.on(new EngineTicked(Instant.parse("2026-06-01T12:00:00Z")));

        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        assertEquals(EngineHealth.Status.OK, liveness.health().status());
    }

    private EngineLiveness liveness(String engineCron) {
        return new EngineLiveness(clock, new GameProperties(Duration.ofSeconds(10), 10_000, 1_000, 1_000,
                List.of(10L), "0 0 * * * *", Duration.ofMinutes(1), 30, "classpath:roster-test.json",
                new GameProperties.Engine(engineCron),
                new GameProperties.AuthLimits(20, Duration.ofMinutes(15), 5, Duration.ofHours(1), 100_000)));
    }
}
