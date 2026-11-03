package com.rdavies.arena.fight.records;

import com.rdavies.arena.Labelled;

import java.time.Instant;

/** Whether the engine is still ticking (GET /health). {@code lastTickAt} is null until a tick commits. */
public record EngineHealth(Status status, Instant lastTickAt) {
    public enum Status implements Labelled {
        OK, STOPPED
    }
}
