package com.rdavies.arena.fight.records;

import java.time.Instant;

/** Whether the engine is still ticking (GET /health/engine). {@code lastTickAt} is null until a tick commits. */
public record EngineHealth(Status status, Instant lastTickAt) {
    public enum Status {
        OK, STOPPED
    }
}
