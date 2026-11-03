package com.rdavies.arena.fight.enums;

import com.rdavies.arena.Labelled;

/** A fight's life: announced (open for betting), live (fighting), settled (result final). */
public enum FightStatus implements Labelled {
    ANNOUNCED, LIVE, SETTLED
}
