package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
/** A finished fight's result. */
public record Outcome(Winner winner, EndedBy endedBy) {}
