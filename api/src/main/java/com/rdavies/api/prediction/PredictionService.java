package com.rdavies.api.prediction;

import com.rdavies.api.GameProperties;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundRepository;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundStatus;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.wallet.WalletService;
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
    private final WalletService walletService;
    private final Clock clock;

    public PredictionService(GameProperties game, RoundRepository roundRepository, RoundService roundService,
                             PredictionRepository predictionRepository, WalletService walletService, Clock clock) {
        this.game = game;
        this.roundRepository = roundRepository;
        this.roundService = roundService;
        this.predictionRepository = predictionRepository;
        this.walletService = walletService;
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

        // 2. Record prediction, locking in the multiplier. The unique (user_id, round_id) constraint
        //    catches a concurrent duplicate that slipped past the check above.
        Prediction saved;
        try {
            saved = predictionRepository.save(new Prediction(null, userId, roundId, type, stake,
                    side.multiplierBps(), PredictionStatus.PENDING, null, now, null));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(ALREADY_PREDICTED, e);
        }

        // 3. Atomic stake deduction and its ledger row; if the balance is too low, the throw rolls back the prediction
        walletService.debitStake(userId, stake, saved.id(), now);
        return saved;
    }
}
