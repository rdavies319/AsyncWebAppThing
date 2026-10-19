package com.rdavies.api.prediction;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("predictions")
public record Prediction(
    @Id Long id,
    Long userId,
    Long roundId,
    PredictionType direction,
    Long stake,
    /** Locked in when the prediction is placed; 10000 = 1.00x. */
    Long multiplierBps,
    PredictionStatus status,
    /** Total credited at settlement (stake included), 0 for a loss; null while pending. */
    Long payout,
    Instant createdAt,
    Instant settledAt
) {}
