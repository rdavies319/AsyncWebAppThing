package com.rdavies.api.prediction;

import com.rdavies.api.round.Odds;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundRepository;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundStatus;
import com.rdavies.api.wallet.LedgerEntry;
import com.rdavies.api.wallet.LedgerRepository;
import com.rdavies.api.wallet.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class PredictionService {
    private final RoundRepository roundRepository;
    private final RoundService roundService;
    private final PredictionRepository predictionRepository;
    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public PredictionService(RoundRepository roundRepository, RoundService roundService,
                             PredictionRepository predictionRepository, WalletRepository walletRepository,
                             LedgerRepository ledgerRepository, Clock clock) {
        this.roundRepository = roundRepository;
        this.roundService = roundService;
        this.predictionRepository = predictionRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    public Optional<Prediction> getPrediction(Long userId, Long roundId) {
        return predictionRepository.findByRoundIdAndUserId(roundId, userId);
    }

    /** The most recently rolled (or voided) round, with this player's prediction on it. */
    public Optional<RoundResult> getLastResult(Long userId) {
        return roundService.getLastFinishedRound()
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
        if (!clock.instant().isBefore(roundService.predictionDeadline(round))) {
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
}
