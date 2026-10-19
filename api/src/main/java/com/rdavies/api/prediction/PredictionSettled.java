package com.rdavies.api.prediction;

import java.util.List;

/** One player's settled prediction: the {@code prediction_settled} SSE payload, sent only to that player. */
public record PredictionSettled(
    long userId,
    long predictionId,
    long roundId,
    PredictionStatus status,
    long payout,
    /** Wallet balance straight after settlement. */
    long balance
) {
    /** Published once per settled round, covering every prediction on it. */
    public record Batch(List<PredictionSettled> predictions) {}
}
