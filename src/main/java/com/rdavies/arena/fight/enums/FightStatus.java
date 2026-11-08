package com.rdavies.arena.fight.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** A fight's life: announced (open for betting), live (fighting), settled (result final). */
public enum FightStatus {
    ANNOUNCED, LIVE, SETTLED;

    @JsonValue
    public String label() {
        return name().toLowerCase();
    }
}
