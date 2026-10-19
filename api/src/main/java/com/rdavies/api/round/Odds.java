package com.rdavies.api.round;

import com.rdavies.api.prediction.PredictionType;

/**
 * Odds for a roll of 1-100 against the current roll R (spec section 3).
 * P(higher) = (100 - R)/100, P(lower) = (R - 1)/100, multiplier = (1 - edge)/P with a 2% edge.
 * Multipliers are integer basis points (10000 = 1.00x), so points never pass through a double.
 */
public final class Odds {
    /** 0.98 (2% house edge) x 100 outcomes x 10000 basis points: multiplier_bps = this / W. */
    private static final long EDGE_ADJUSTED_BPS = 980_000;
    private static final long BPS_PER_UNIT = 10_000;

    private Odds() {}

    /** Number of rolls (out of 100) that win this prediction. */
    public static int winningOutcomes(int currentRoll, PredictionType type) {
        return type == PredictionType.HIGHER ? 100 - currentRoll : currentRoll - 1;
    }

    public static boolean isAllowed(int currentRoll, PredictionType type) {
        return winningOutcomes(currentRoll, type) > 0;
    }

    /** floor(980000 / W), locked in on the prediction when it is placed. */
    public static long multiplierBps(int currentRoll, PredictionType type) {
        int outcomes = winningOutcomes(currentRoll, type);
        if (outcomes <= 0) {
            throw new IllegalArgumentException(type + " cannot win against " + currentRoll);
        }
        return EDGE_ADJUSTED_BPS / outcomes;
    }

    /** Total credited to a winner, stake included. Must match PredictionRepository.settleRound. */
    public static long payout(long stake, long multiplierBps) {
        return Math.multiplyExact(stake, multiplierBps) / BPS_PER_UNIT;
    }

    /** Largest stake whose payout stays within maxPayout: min(maxStake, floor(maxPayout * 10000 / bps)). */
    public static long maxStake(long multiplierBps, long maxStake, long maxPayout) {
        return Math.min(maxStake, Math.multiplyExact(maxPayout, BPS_PER_UNIT) / multiplierBps);
    }
}
