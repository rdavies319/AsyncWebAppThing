package com.rdavies.arena.bet.enums;

import com.rdavies.arena.Labelled;

/** A bet's life: pending until its fight settles, then won, lost or push (a draw, stake refunded). */
public enum BetStatus implements Labelled {
    PENDING, WON, LOST, PUSH
}
