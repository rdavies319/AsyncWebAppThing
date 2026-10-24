package com.rdavies.arena.fight.records;

/** One of a gladiator's latest opponents, for pairing. */
public record RecentOpponent(long gladiatorId, long opponentId) {}
