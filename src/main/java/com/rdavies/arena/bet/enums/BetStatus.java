package com.rdavies.arena.bet.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** A bet's life: pending until its fight settles, then won, lost or push (a draw, stake refunded). */
public enum BetStatus {
    PENDING, WON, LOST, PUSH;

    @JsonValue
    public String label() {
        return name().toLowerCase();
    }
}
