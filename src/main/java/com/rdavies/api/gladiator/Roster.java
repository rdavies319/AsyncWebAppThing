package com.rdavies.api.gladiator;

import com.rdavies.api.gladiator.records.RosterEntry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

/**
 * Reads a roster file (spec section 3): a JSON array of gladiators, each with a snake_case
 * {@code stats} block. The real roster lives outside git; dev and tests use {@code roster-dev.json}.
 */
public final class Roster {
    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private Roster() {}

    /** @throws IllegalArgumentException if the file isn't a valid roster */
    public static List<RosterEntry> read(InputStream in) {
        try {
            return List.copyOf(JSON.readValue(in, new TypeReference<List<RosterEntry>>() {}));
        } catch (DatabindException e) {
            // Unwrap a stat block's own refusal; never echo the file's contents, which hold the stats
            Throwable cause = e.getCause() instanceof IllegalArgumentException invalid ? invalid : null;
            throw new IllegalArgumentException("invalid roster file", cause);
        }
    }
}
