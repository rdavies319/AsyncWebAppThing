package com.rdavies.api.round;

import java.time.Instant;

/**
 * The open round as clients see it: the {@code round_open} SSE payload and the
 * {@code GET /rounds/current} response (spec section 8).
 * A side is null when it cannot win (Higher on 100, Lower on 1).
 */
public record RoundView(
    long roundId,
    int roll,
    Instant startsAt,
    Instant cutoffAt,
    Side higher,
    Side lower,
    /** Commitment to the seed of the roll being predicted. */
    String nextSeedCommitHash,
    /** When this view was built, so clients can count down by the server clock. */
    Instant serverTime
) {
    /** When the next round is rolled. Not a getter, so it stays out of the JSON. */
    public Instant rollAt() {
        return startsAt.plus(Round.LENGTH);
    }

    public record Side(long multiplierBps, long maxStake) {
        public String multiplier() {
            return Odds.format(multiplierBps);
        }
    }
}
