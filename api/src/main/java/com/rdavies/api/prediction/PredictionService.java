package com.rdavies.api.prediction;

import com.rdavies.api.GameProperties;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundRepository;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundStatus;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.wallet.LedgerEntry;
import com.rdavies.api.wallet.LedgerRepository;
import com.rdavies.api.wallet.WalletRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class PredictionService {
    private static final String ALREADY_PREDICTED = "You have already predicted this round";

    private final GameProperties game;
    private final RoundRepository roundRepository;
    private final RoundService roundService;
    private final PredictionRepository predictionRepository;
    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public PredictionService(GameProperties game, RoundRepository roundRepository, RoundService roundService,
                             PredictionRepository predictionRepository, WalletRepository walletRepository,
                             LedgerRepository ledgerRepository, Clock clock) {
        this.game = game;
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

    @Transactional
    public Prediction placePrediction(Long userId, Long roundId, PredictionType type, Long stake) {
        if (roundId == null) {
            throw new IllegalArgumentException("Choose a round");
        }
        if (type == null) {
            throw new IllegalArgumentException("Choose higher or lower");
        }
        if (stake == null) {
            throw new IllegalArgumentException("Enter a stake");
        }

        // 1. Verify round is still OPEN and its deadline hasn't passed. FOR SHARE keeps the engine
        //    from settling the round until this prediction has committed.
        Round round = roundRepository.lockForPrediction(roundId)
                .filter(r -> r.status() == RoundStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Round is no longer open for predictions"));
        Instant now = clock.instant();
        if (!now.isBefore(roundService.predictionDeadline(round))) {
            throw new IllegalStateException("Predictions for this round have closed");
        }
        // The same odds and stake limit the page shows
        RoundView.Side side = roundService.side(round, type).orElseThrow(
                () -> new IllegalArgumentException(type + " cannot win against " + round.rollValue()));
        if (stake < game.minStake() || stake > side.maxStake()) {
            throw new IllegalArgumentException(
                    "Stake must be between " + game.minStake() + " and " + side.maxStake() + " pts on " + type);
        }
        if (predictionRepository.existsByRoundIdAndUserId(roundId, userId)) {
            throw new IllegalStateException(ALREADY_PREDICTED);
        }

        // 2. Atomic stake deduction
        int affected = walletRepository.deductStake(userId, stake, now);
        if (affected == 0) {
            throw new IllegalStateException("Insufficient funds in wallet");
        }

        // 3. Record prediction, locking in the multiplier. The unique (user_id, round_id) constraint
        //    catches a concurrent duplicate that slipped past the check above; rethrowing rolls back the debit.
        Prediction saved;
        try {
            saved = predictionRepository.save(new Prediction(null, userId, roundId, type, stake,
                    side.multiplierBps(), PredictionStatus.PENDING, null, now, null));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(ALREADY_PREDICTED, e);
        }

        // 4. Audit ledger
        ledgerRepository.save(new LedgerEntry(null, userId, -stake, LedgerEntry.Reason.STAKE, saved.id(), now));
        return saved;
    }
}
