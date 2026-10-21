package com.rdavies.api.prediction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PredictionType {
    HIGHER, LOWER;

    /** JSON uses the lower-case names from spec section 7, and accepts any case. */
    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PredictionType fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
