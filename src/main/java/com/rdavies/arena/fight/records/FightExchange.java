package com.rdavies.arena.fight.records;

import java.time.Instant;

/**
 * A stored exchange, raw rolls included (internal). Public views use {@link ExchangeView}.
 *
 * @param dueAt when it was due; runAt is when the engine ran it
 */
public record FightExchange(long fightId, Instant dueAt, Instant runAt, ExchangeResult result) {}
