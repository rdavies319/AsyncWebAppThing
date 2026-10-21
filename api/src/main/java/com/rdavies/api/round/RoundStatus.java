package com.rdavies.api.round;

public enum RoundStatus {
    /** Seed committed, not yet rolled: the roll players are currently predicting. */
    SCHEDULED,
    /** Rolled; taking predictions until the cutoff. */
    OPEN,
    /** Its predictions have been settled against the next round's roll. */
    SETTLED
}
