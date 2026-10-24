package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.GameRuleException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
        if (!engine.tick()) {
            throw busy();
        }
    }

    /** Runs the live fight's next round now. */
    @Transactional
    public void round() {
        requireLive();
        runNextRound();
    }

    /** Runs the live fight's rounds now, until it settles. */
    @Transactional
    public void finish() {
        requireLive();
        // Each tick runs one round, and a fight never runs past the cap
        for (int i = 0; i < game.maxRounds() && fights.findLive().isPresent(); i++) {
            runNextRound();
        }
    }

    private void requireLive() {
        if (fights.findLive().isEmpty()) {
            throw GameRuleException.conflict("No fight is live");
        }
    }

    private void runNextRound() {
        fights.pullRoundForward(clock.instant());
        if (!engine.tick()) {
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
