package com.rdavies.arena.fight.events;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;

/** A fight's result is final (SSE fight_settled). The new Elo ratings join it in task 06. */
public record FightSettled(long fightId, Winner winner, EndedBy endedBy) {}
