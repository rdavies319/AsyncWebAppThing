package com.rdavies.arena.fight.records;

import java.util.List;

/** One fight's page (GET /fights/{id}): the fight and its public round log so far, oldest first. */
public record FightPage(FightRecord fight, List<RoundView> rounds) {
    public FightPage {
        rounds = List.copyOf(rounds);
    }
}
