package com.rdavies.arena.fight.events;

import java.time.Instant;

/** A tick ran to its end. Not sent to clients: only {@code EngineLiveness} listens, after commit. */
public record EngineTicked(Instant at) {}
