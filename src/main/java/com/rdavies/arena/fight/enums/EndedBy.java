package com.rdavies.arena.fight.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** How a fight ended (spec section 4, Ending). */
public enum EndedBy {
    /** Exactly one gladiator at HP 0 or below. */
    KO,
    /** Both at HP 0 or below in the same round: a draw. */
    DOUBLE_KO,
    /** The round cap, decided on remaining HP share. */
    CAP;

    @JsonValue
    public String label() {
        return name().toLowerCase();
    }
}
