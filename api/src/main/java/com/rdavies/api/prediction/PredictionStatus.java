package com.rdavies.api.prediction;

public enum PredictionStatus {
    PENDING, WON, LOST,
    /** The roll tied the previous one: stake refunded. */
    PUSH
}
