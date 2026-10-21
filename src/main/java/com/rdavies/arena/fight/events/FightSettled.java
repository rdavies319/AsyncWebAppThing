package com.rdavies.arena.fight.events;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;

/**
 * A fight's result is final (SSE fight_settled).
 *
 * @param redElo red's new rating, after this fight
 */
public record FightSettled(long fightId, Winner winner, EndedBy endedBy, int redElo, int blueElo) {}
