package com.rdavies.arena;

import com.fasterxml.jackson.annotation.JsonValue;

/** An enum whose constants appear in lower case, in JSON and in roll messages: "red", "double_ko". */
public interface Labelled {
    String name();

    @JsonValue
    default String label() {
        return name().toLowerCase();
    }
}
