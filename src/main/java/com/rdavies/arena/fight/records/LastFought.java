package com.rdavies.arena.fight.records;

import java.time.Instant;

/** When a gladiator's most recent fight started, for pairing. */
public record LastFought(long gladiatorId, Instant startsAt) {}
