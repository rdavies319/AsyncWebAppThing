package com.rdavies.api.round;

/**
 * Published by the round engine when a round's predictions are settled: the {@code round_settled}
 * SSE payload. The next round has just been rolled, so its seed is revealed for verification.
 */
public record RoundSettled(long roundId, long nextRoundId, int nextRoll, String nextRoundSeed) {}
