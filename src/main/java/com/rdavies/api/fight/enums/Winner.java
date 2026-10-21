package com.rdavies.api.fight.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** Who won a fight. */
public enum Winner {
    RED, BLUE, DRAW;

    @JsonValue
    public String label() {
        return name().toLowerCase();
    }
}
