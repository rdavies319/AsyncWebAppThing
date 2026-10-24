package com.rdavies.arena.gladiator;

import com.rdavies.arena.GameProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Loads game.roster-file on first boot, before the fight engine's startup tick needs gladiators. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RosterLoader implements ApplicationRunner {
    private final GladiatorService gladiators;
    private final ResourceLoader resources;
    private final GameProperties game;

    RosterLoader(GladiatorService gladiators, ResourceLoader resources, GameProperties game) {
        this.gladiators = gladiators;
        this.resources = resources;
        this.game = game;
    }

    @Override
    public void run(ApplicationArguments args) {
        gladiators.loadRosterIfEmpty(resources.getResource(game.rosterFile()));
    }
}
