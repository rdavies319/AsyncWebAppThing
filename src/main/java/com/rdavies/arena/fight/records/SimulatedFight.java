package com.rdavies.arena.fight.records;

import java.util.List;

/** A whole fight played out from its seed: every round in order, and how it ended. */
public record SimulatedFight(List<RoundResult> rounds, Outcome outcome) {
    public SimulatedFight {
        rounds = List.copyOf(rounds);
    }
}
