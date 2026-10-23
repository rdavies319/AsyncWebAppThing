package com.rdavies.arena.fight.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** A fight's two sides. */
public enum Corner {
    RED, BLUE;

    /** The name used in roll messages and JSON: "red" or "blue". */
    @JsonValue
    public String label() {
        return name().toLowerCase();
    }

    /** The corner with this label ("red" or "blue"), or null for anything else. */
    public static Corner fromLabel(String label) {
        for (Corner corner : values()) {
            if (corner.label().equals(label)) {
                return corner;
            }
        }
        return null;
    }

    public Corner other() {
        return this == RED ? BLUE : RED;
    }
}
