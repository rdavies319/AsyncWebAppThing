package com.rdavies.api.service;

import com.rdavies.api.domain.*;
import com.rdavies.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class GameService {
    private final RoundRepository roundRepository;
    private final PredictionRepository predictionRepository;
    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public GameService(RoundRepository roundRepository, PredictionRepository predictionRepository,
                       WalletRepository walletRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.roundRepository = roundRepository;
        this.predictionRepository = predictionRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    /** Predictions close this long before the roll at the next minute boundary. */
    static final Duration PREDICTION_CUTOFF = Duration.ofSeconds(5);

    public Optional<Round> getCurrentRound() {
        return roundRepository.findCurrentOpenRound();
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

    public Optional<Prediction> getPrediction(Long userId, Long roundId) {
        return predictionRepository.findByRoundIdAndUserId(roundId, userId);
    }

    /** The most recently rolled (or voided) round, with this player's prediction on it. */
    public Optional<RoundResult> getLastResult(Long userId) {
        return roundRepository.findLastFinishedRound()
                .map(r -> new RoundResult(r, getPrediction(userId, r.roundId()).orElse(null)));
    }

    @Transactional
    public void placePrediction(Long userId, Long roundId, PredictionType type, Long stake) {
        if (type == null) {
            throw new IllegalArgumentException("Choose higher or lower");
        }
        if (stake == null || stake <= 0) {
            throw new IllegalArgumentException("Stake must be a positive number of points");
        }

        // 1. Verify round is still OPEN and its deadline hasn't passed
        Round round = roundRepository.findById(roundId)
                .filter(r -> r.status() == RoundStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Round is no longer open for predictions"));
        if (!clock.instant().isBefore(predictionDeadline(round))) {
            throw new IllegalStateException("Predictions for this round have closed");
        }
        if (!Odds.isAllowed(round.previousRoll(), type)) {
            throw new IllegalArgumentException(type + " cannot win against " + round.previousRoll());
        }
        if (predictionRepository.existsByRoundIdAndUserId(roundId, userId)) {
            throw new IllegalStateException("You have already predicted this round");
        }

        // 2. Atomic stake deduction
        int affected = walletRepository.deductStake(userId, stake);
        if (affected == 0) {
            throw new IllegalStateException("Insufficient funds in wallet");
        }

        // 3. Record prediction
        Instant now = clock.instant();
        Prediction prediction = new Prediction(null, roundId, userId, type, stake, now);
        predictionRepository.save(prediction);

        // 4. Audit ledger
        ledgerRepository.save(new LedgerEntry(null, userId, roundId, -stake, LedgerEntry.LedgerType.STAKE, now));
    }

    public Wallet getWallet(Long userId) {
        return walletRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user"));
    }
}
