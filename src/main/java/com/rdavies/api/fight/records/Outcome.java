package com.rdavies.api.fight.records;

import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;
/** A finished fight's result. */
public record Outcome(Winner winner, EndedBy endedBy) {}
