package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.StringJoiner;

/**
 * Dev profile only: a fresh arena starts from a simulated season's ratings ({@link EloSeason})
 * rather than everyone on 1000, so the first fights already have uneven odds. Runs at startup after
 * the roster loads and before the engine's first tick. A cron of "-" turns it off, along with that
 * tick, so dev tests never commit seeded ratings.
 */
@Component
@Profile("dev")
@Order(0)
class DevRatings implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevRatings.class);

    private final FightRepository fights;
    private final GladiatorService gladiators;
    private final GameProperties game;

    DevRatings(FightRepository fights, GladiatorService gladiators, GameProperties game) {
        this.fights = fights;
        this.gladiators = gladiators;
        this.game = game;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (game.engine().scheduled()) {
            seedIfFresh();
        }
    }

    /**
     * Sets every gladiator's rating from the simulated season, if no fight exists yet.
     *
     * @return whether the ratings were seeded
     */
    @Transactional
    boolean seedIfFresh() {
        if (fights.count() > 0) {
            return false;
        }
        List<Gladiator> roster = gladiators.roster();
        List<Integer> ratings = EloSeason.replay(roster.stream().map(g -> gladiators.stats(g.id())).toList(),
                game.maxRounds());
        StringJoiner seeded = new StringJoiner(", ");
        for (int i = 0; i < roster.size(); i++) {
            gladiators.seedElo(roster.get(i).id(), ratings.get(i));
            seeded.add(roster.get(i).slug() + "=" + ratings.get(i));
        }
        log.info("Dev ratings seeded from a simulated season: {}", seeded); // public ratings only, never the stats
        return true;
    }
}
