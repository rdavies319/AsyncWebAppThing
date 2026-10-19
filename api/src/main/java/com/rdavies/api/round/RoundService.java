package com.rdavies.api.round;

import com.rdavies.api.GameProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class RoundService {
    private final RoundRepository roundRepository;
    private final GameProperties game;
    private final Clock clock;

    public RoundService(RoundRepository roundRepository, GameProperties game, Clock clock) {
        this.roundRepository = roundRepository;
        this.game = game;
        this.clock = clock;
    }

    public Optional<Round> getCurrentRound() {
        return roundRepository.findOpenRound();
    }

    /** Commitment to the seed of the roll players are currently predicting. */
    public Optional<String> getNextRollCommit() {
        return roundRepository.findScheduledRound().map(Round::seedCommitHash);
    }

    /** The most recently settled round, with the roll it settled against. */
    public Optional<SettledRound> getLastSettledRound() {
        return roundRepository.findLastSettledRound().map(round -> new SettledRound(round,
                roundRepository.findRoundAfter(round.id()).orElseThrow().rollValue()));
    }

    public Instant now() {
        return clock.instant();
    }

    /** When the next round is rolled and this one settled, by the server clock. */
    public Instant rollTime(Round round) {
        return round.startsAt().plus(Duration.ofMinutes(1));
    }

    /** When predictions close for this round, by the server clock: game.cutoff before the next roll. */
    public Instant predictionDeadline(Round round) {
        return rollTime(round).minus(game.cutoff());
    }

    /** A settled round and the next round's roll, which its predictions were settled against. */
    public record SettledRound(Round round, int nextRoll) {}
}
