package com.rdavies.api.round;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class RoundService {
    private final RoundRepository roundRepository;
    private final Clock clock;

    public RoundService(RoundRepository roundRepository, Clock clock) {
        this.roundRepository = roundRepository;
        this.clock = clock;
    }

    /** Predictions close this long before the roll at the next minute boundary. */
    static final Duration PREDICTION_CUTOFF = Duration.ofSeconds(5);

    public Optional<Round> getCurrentRound() {
        return roundRepository.findCurrentOpenRound();
    }

    /** The most recently rolled (or voided) round. */
    public Optional<Round> getLastFinishedRound() {
        return roundRepository.findLastFinishedRound();
    }

    public Instant now() {
        return clock.instant();
    }

    /** When this round is rolled, by the server clock. */
    public Instant rollTime(Round round) {
        return round.startTime().truncatedTo(ChronoUnit.MINUTES).plus(Duration.ofMinutes(1));
    }

    /** When predictions close for this round, by the server clock. */
    public Instant predictionDeadline(Round round) {
        return rollTime(round).minus(PREDICTION_CUTOFF);
    }
}
