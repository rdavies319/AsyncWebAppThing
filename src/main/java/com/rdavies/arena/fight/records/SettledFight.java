package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;

/** A settled fight as anyone may see it: who fought, the HP they finished on, and how it ended. */
public record SettledFight(long fightId, LiveFight.Fighter red, LiveFight.Fighter blue, Winner winner,
                           EndedBy endedBy, int roundsDone) {

    public LiveFight.Fighter fighter(Corner corner) {
        return corner == Corner.RED ? red : blue;
    }
}
