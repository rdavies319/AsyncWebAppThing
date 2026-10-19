package com.rdavies.api.domain;

public enum RoundStatus {
    OPEN, CLOSED, SETTLED,
    /** Missed by the engine during downtime: no roll, every stake refunded. */
    VOIDED
}
