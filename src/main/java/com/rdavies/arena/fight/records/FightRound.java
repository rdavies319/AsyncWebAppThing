package com.rdavies.arena.fight.records;

import java.time.Instant;

/**
 * A stored round, raw rolls included (internal). Public views use {@link RoundView}.
 *
 * @param dueAt when it was due; runAt is when the engine ran it
 */
public record FightRound(long fightId, Instant dueAt, Instant runAt, RoundResult result) {}
