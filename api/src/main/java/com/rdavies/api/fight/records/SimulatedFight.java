package com.rdavies.api.fight.records;

import java.util.List;

/** A whole fight played out from its seed: every exchange in order, and how it ended. */
public record SimulatedFight(List<ExchangeResult> exchanges, Outcome outcome) {
    public SimulatedFight {
        exchanges = List.copyOf(exchanges);
    }
}
