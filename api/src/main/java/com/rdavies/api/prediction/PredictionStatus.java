package com.rdavies.api.prediction;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PredictionStatus {
    PENDING, WON, LOST,
    /** The roll tied the previous one: stake refunded. */
    PUSH;

    /** JSON uses the lower-case names from spec section 7. */
    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
