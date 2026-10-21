package com.rdavies.api.round;

import com.rdavies.api.GameProperties;
import com.rdavies.api.prediction.PredictionType;
import org.springframework.stereotype.Service;

import java.time.Clock;
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

    /** The open round with its odds and the next roll's commitment, as sent to clients. */
    public Optional<RoundView> currentView() {
        return roundRepository.findOpenRound().map(round -> view(round, getNextRollCommit().orElse(null)));
    }

    /** An open round as clients see it, given the commitment to the seed of the roll being predicted. */
    public RoundView view(Round open, String nextSeedCommitHash) {
        return new RoundView(open.id(), open.rollValue(), open.startsAt(), predictionDeadline(open),
                side(open, PredictionType.HIGHER).orElse(null), side(open, PredictionType.LOWER).orElse(null),
                nextSeedCommitHash, clock.instant());
    }

    /** The odds and stake limit on one side of this round, or empty if that side cannot win. */
    public Optional<RoundView.Side> side(Round round, PredictionType direction) {
        if (!Odds.isAllowed(round.rollValue(), direction)) {
            return Optional.empty();
        }
        long bps = Odds.multiplierBps(round.rollValue(), direction);
        return Optional.of(new RoundView.Side(bps, Odds.maxStake(bps, game.maxStake(), game.maxPayout())));
    }

    /** Commitment to the seed of the roll players are currently predicting. */
    private Optional<String> getNextRollCommit() {
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

    /** When predictions close for this round, by the server clock: game.cutoff before the next roll. */
    public Instant predictionDeadline(Round round) {
        return round.rollTime().minus(game.cutoff());
    }

    /** A settled round and the next round's roll, which its predictions were settled against. */
    public record SettledRound(Round round, int nextRoll) {}
}
