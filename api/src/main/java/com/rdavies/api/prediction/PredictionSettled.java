package com.rdavies.api.prediction;

/** One player's settled prediction: the {@code prediction_settled} SSE payload, sent only to that player. */
public record PredictionSettled(
    long userId,
    long predictionId,
    long roundId,
    PredictionStatus status,
    long payout,
    /** Wallet balance straight after settlement. */
    long balance
) {}
