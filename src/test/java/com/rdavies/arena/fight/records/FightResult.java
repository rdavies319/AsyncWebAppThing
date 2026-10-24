package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;

/** How a settled fight ended, with the HP each side was left on. Test-only: TestFights reads it from the row. */
public record FightResult(long fightId, Winner winner, EndedBy endedBy, int roundsDone, int redHp, int blueHp) {}
