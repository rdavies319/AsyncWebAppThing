package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.Winner;

/** Elo ratings from spec section 5, as pure functions. Ratings are integers and every change is zero-sum. */
final class Elo {
    static final int K = 32;

    private Elo() {}

    /** Red's expected score against blue, unclamped: 1 / (1 + 10^((blue − red) / 400)). */
    static double expectedRed(int redElo, int blueElo) {
        return 1 / (1 + Math.pow(10, (blueElo - redElo) / 400.0));
    }

    /** How far red's rating moves after the fight; blue's moves the opposite way. */
    static int redDelta(int redElo, int blueElo, Winner winner) {
        double score = switch (winner) {
            case RED -> 1;
            case DRAW -> 0.5;
            case BLUE -> 0;
        };
        return (int) Math.round(K * (score - expectedRed(redElo, blueElo)));
    }
}
