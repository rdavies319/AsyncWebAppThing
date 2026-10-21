package com.rdavies.api.fight.records;

import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;

/** A settled fight as anyone may see it: who fought, the HP they finished on, and how it ended. */
public record SettledFight(long fightId, LiveFight.Fighter red, LiveFight.Fighter blue, Winner winner,
                           EndedBy endedBy, int exchangesDone) {}
