package com.rdavies.arena.fight.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** Who won a fight. */
public enum Winner {
    RED, BLUE, DRAW;

    @JsonValue
    public String label() {
        return name().toLowerCase();
    }
}
