package com.rdavies.arena.fight.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** How a settled fight went for one gladiator. */
public enum Result {
    WON, LOST, DREW;

    @JsonValue
    public String label() {
        return name().toLowerCase();
    }
}
