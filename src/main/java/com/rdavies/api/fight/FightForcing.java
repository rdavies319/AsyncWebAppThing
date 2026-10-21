package com.rdavies.api.fight;

import com.rdavies.api.GameProperties;
import com.rdavies.api.GameRuleException;
import com.rdavies.api.fight.records.Fight;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Dev forcing (spec section 7, Dev controls), under the dev profile only. Each verb pulls a due time
 * forward to now and runs the real engine tick, so forcing takes the same code path and lock as
 * production and never changes a result: the seed has already fixed it.
 */
@Service
@Profile("dev")
public class FightForcing {
    private final FightRepository fights;
    private final FightEngine engine;
    private final GameProperties game;
    private final Clock clock;

    FightForcing(FightRepository fights, FightEngine engine, GameProperties game, Clock clock) {
        this.fights = fights;
        this.engine = engine;
        this.game = game;
        this.clock = clock;
    }

    /** Starts the announced fight now. */
    @Transactional
    public void start() {
        if (fights.findLive().isPresent()) {
            throw GameRuleException.conflict("A fight is already live");
        }
        if (fights.findAnnounced().isEmpty()) {
            throw GameRuleException.conflict("No fight is announced");
        }
        fights.pullStartForward(clock.instant());
        engine.tick();
        if (fights.findLive().isEmpty()) {
            throw busy();
        }
    }

    /** Runs the live fight's next exchange now. */
    @Transactional
    public void exchange() {
        runNextExchange(requireLive());
    }

    /** Runs the live fight's exchanges now, until it settles. */
    @Transactional
    public void finish() {
        Optional<Fight> live = Optional.of(requireLive());
        // Each tick runs one exchange, and a fight never runs past the cap
        for (int i = 0; i < game.maxExchanges() && live.isPresent(); i++) {
            runNextExchange(live.get());
            live = fights.findLive();
        }
    }

    private Fight requireLive() {
        return fights.findLive().orElseThrow(() -> GameRuleException.conflict("No fight is live"));
    }

    private void runNextExchange(Fight live) {
        fights.pullExchangeForward(clock.instant());
        engine.tick();
        if (fights.findLive().filter(after -> after.exchangesDone() == live.exchangesDone()).isPresent()) {
            throw busy();
        }
    }

    /**
     * The forced tick found another tick holding the arena lock, so it skipped. Throwing rolls back
     * the pull forward too, rather than leaving the fight for the other tick to start unannounced.
     */
    private static GameRuleException busy() {
        return GameRuleException.conflict("The engine is busy; try again");
    }
}
