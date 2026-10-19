package com.rdavies.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("predictions")
public record Prediction(
    @Id Long predictionId,
    Long roundId,
    Long userId,
    PredictionType prediction,
    Long stake,
    Instant createdAt
) {}
