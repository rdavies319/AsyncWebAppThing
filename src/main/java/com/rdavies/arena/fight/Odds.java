package com.rdavies.arena.fight;

import java.util.List;

/**
 * Fixed odds from public Elo (spec section 5, Odds), as pure functions. The chance is the game's one
 * floating-point calculation, rounded once; everything after it is integer maths in basis points.
 */
final class Odds {
    static final int MIN_CHANCE_BPS = 1000;
    static final int MAX_CHANCE_BPS = 9000;

    private Odds() {}

    /** Red's chance of winning, in basis points: the Elo expected score, rounded once and clamped to 10–90%. */
    static int redChanceBps(int redElo, int blueElo) {
        int bps = (int) Math.round(10_000 * Elo.expectedRed(redElo, blueElo));
        return Math.clamp(bps, MIN_CHANCE_BPS, MAX_CHANCE_BPS);
    }

    /** What a winning bet pays per point staked, stake included, in basis points: 0.98 / chance, a 2% edge. */
    static int multiplierBps(int chanceBps) {
        return 98_000_000 / chanceBps;
    }

    /** The stake options allowed at this multiplier: those whose win can't pay more than max-payout. */
    static List<Long> stakes(List<Long> stakeOptions, long maxPayout, int multiplierBps) {
        long most = maxPayout * 10_000 / multiplierBps;
        return stakeOptions.stream().filter(stake -> stake <= most).toList();
    }
}
