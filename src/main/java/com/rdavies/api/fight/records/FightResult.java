package com.rdavies.api.fight.records;

import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;

/** How a settled fight ended, with the HP each side was left on. */
public record FightResult(long fightId, Winner winner, EndedBy endedBy, int exchangesDone, int redHp, int blueHp) {}
