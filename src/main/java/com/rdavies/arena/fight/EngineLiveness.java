package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.fight.events.EngineTicked;
import com.rdavies.arena.fight.records.EngineHealth;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Whether the engine is still ticking, not just whether the app answers. Only a tick that commits
 * counts, so one that fails, rolls back or skips on the arena lock changes nothing. The limit is counted in
 * scheduled ticks, so it follows game.engine.cron. The clock starts at startup, so a fresh app isn't
 * unhealthy before its first tick. With the schedule off, no tick is expected.
 */
@Component
public class EngineLiveness {
    /** How many scheduled ticks may pass after the last good one before the engine counts as stopped. */
    private static final int TICKS_ALLOWED = 3;

    private final Clock clock;
    private final CronExpression engineCron;
    private final Instant startedAt;
    private volatile Instant lastTickAt;

    EngineLiveness(Clock clock, GameProperties game) {
        this.clock = clock;
        this.engineCron = game.engine().scheduled() ? CronExpression.parse(game.engine().cron()) : null;
        this.startedAt = clock.instant();
    }

    @TransactionalEventListener
    void on(EngineTicked tick) {
        lastTickAt = tick.at();
    }

    public EngineHealth health() {
        Instant last = lastTickAt;
        boolean stopped = engineCron != null
                && clock.instant().isAfter(lastAllowedTick(last != null ? last : startedAt));
        return new EngineHealth(stopped ? EngineHealth.Status.STOPPED : EngineHealth.Status.OK, last);
    }

    private Instant lastAllowedTick(Instant after) {
        ZonedDateTime tick = after.atZone(ZoneOffset.UTC);
        for (int i = 0; i < TICKS_ALLOWED; i++) {
            tick = engineCron.next(tick);
        }
        return tick.toInstant();
    }
}
