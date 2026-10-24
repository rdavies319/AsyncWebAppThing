package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Result;

import java.time.Instant;

/** One of a gladiator's settled fights, from their side. */
public record RecentResult(long fightId, Instant startsAt, String opponentSlug, String opponentName,
                           Result result, EndedBy endedBy) {}
