package com.rdavies.arena.fight.records;

/**
 * A round as anyone may see it: who hit, for how much, and the HP after. No raw rolls.
 *
 * @param redHit    whether red's attack landed; redDamage is what it did to blue
 */
public record RoundView(int n, boolean redHit, int redDamage, boolean blueHit, int blueDamage,
                           int redHpAfter, int blueHpAfter) {

    public static RoundView of(RoundResult round) {
        return new RoundView(round.n(), round.red().hit(), round.red().damage(),
                round.blue().hit(), round.blue().damage(), round.redHpAfter(), round.blueHpAfter());
    }
}
