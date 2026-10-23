package com.rdavies.arena.fight.records;

/**
 * A gladiator's record over their settled fights (spec section 9), computed from public columns
 * only. Rates are in basis points and averages in hundredths, so the maths stays integer; each is
 * null when there is nothing to divide by.
 *
 * @param winRateBps         wins ÷ fights
 * @param koRateBps          KO wins ÷ wins
 * @param avgRoundsX100      average fight length in rounds, × 100
 * @param roundsFought       rounds fought across all settled fights
 * @param avgDamageDealtX100 damage dealt per round, misses included, × 100
 * @param avgDamageTakenX100 damage taken per round, dodges included, × 100
 * @param dodgeRateBps       attacks dodged ÷ attacks received
 */
public record Career(int fights, int wins, int losses, int draws, Integer winRateBps, Integer koRateBps,
                     Integer avgRoundsX100, int roundsFought, Integer avgDamageDealtX100,
                     Integer avgDamageTakenX100, Integer dodgeRateBps) {}
