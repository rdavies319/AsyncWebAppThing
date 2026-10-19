package com.rdavies.api.service;

import com.rdavies.api.domain.PredictionType;

/**
 * Odds for a roll of 1-100 against the previous roll R.
 * P(higher) = (100 - R)/100, P(lower) = (R - 1)/100, multiplier = (1 - edge)/P.
 * Done in integer maths so points are never rounded through a double.
 */
public final class Odds {
    /** House edge of 2%, expressed as the percentage of fair value paid out. */
    private static final long PAYOUT_PERCENT = 98;

    private Odds() {}

    /** Number of rolls (out of 100) that win this prediction. */
    public static int winningOutcomes(int previousRoll, PredictionType type) {
        return type == PredictionType.HIGHER ? 100 - previousRoll : previousRoll - 1;
    }

    public static boolean isAllowed(int previousRoll, PredictionType type) {
        return winningOutcomes(previousRoll, type) > 0;
    }

    /** Total returned to a winner, stake included: floor(stake * 0.98 * 100 / winningOutcomes). */
    public static long payout(int previousRoll, PredictionType type, long stake) {
        int outcomes = winningOutcomes(previousRoll, type);
        if (outcomes <= 0) {
            throw new IllegalArgumentException(type + " cannot win against " + previousRoll);
        }
        return Math.multiplyExact(stake, PAYOUT_PERCENT) / outcomes;
    }
}
