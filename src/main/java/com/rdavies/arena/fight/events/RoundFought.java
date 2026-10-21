package com.rdavies.arena.fight.events;

import com.rdavies.arena.fight.records.RoundView;

/** A round ran (SSE round): who hit, for how much, and the HP after. No raw rolls. */
public record RoundFought(long fightId, int n, boolean redHit, int redDamage, boolean blueHit, int blueDamage,
                          int redHpAfter, int blueHpAfter) {

    public static RoundFought of(long fightId, RoundView round) {
        return new RoundFought(fightId, round.n(), round.redHit(), round.redDamage(),
                round.blueHit(), round.blueDamage(), round.redHpAfter(), round.blueHpAfter());
    }
}
