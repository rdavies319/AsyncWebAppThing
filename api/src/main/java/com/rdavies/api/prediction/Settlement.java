package com.rdavies.api.prediction;

import com.rdavies.api.wallet.WalletService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Settles a round's predictions against the next roll (spec sections 3 and 6): outcome, payout and
 * refund credits, and each player's result. Set-based, so 1,000 predictions settle in a few statements.
 */
@Service
public class Settlement {
    private final PredictionRepository predictionRepository;
    private final WalletService walletService;
    private final JdbcTemplate jdbc;

    public Settlement(PredictionRepository predictionRepository, WalletService walletService, JdbcTemplate jdbc) {
        this.predictionRepository = predictionRepository;
        this.walletService = walletService;
        this.jdbc = jdbc;
    }

    /**
     * Settles every pending prediction on the round, where {@code previousRoll} is the round's own
     * roll and {@code roll} the next round's. A win pays floor(stake × multiplier_bps / 10000) using
     * the multiplier locked in when placed, a push refunds the stake, and a loss pays nothing (the
     * stake was taken when placed). Each credit gets its ledger row.
     *
     * <p>Runs in the caller's transaction (the engine's tick), so events built from the result are
     * sent only once it commits. Settling a round that has nothing pending does nothing and returns an
     * empty batch, so a repeat call never pays twice.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PredictionSettled.Batch settle(long roundId, int previousRoll, int roll, Instant now) {
        int settled = predictionRepository.settleRound(roundId,
                outcome(PredictionType.HIGHER, previousRoll, roll).name(),
                outcome(PredictionType.LOWER, previousRoll, roll).name(), now);
        if (settled == 0) {
            return new PredictionSettled.Batch(List.of());
        }
        walletService.creditRoundPayouts(roundId, now);
        return new PredictionSettled.Batch(jdbc.query("""
                SELECT p.user_id, p.id AS prediction_id, p.round_id, p.status, p.payout, w.balance
                FROM predictions p JOIN wallets w ON w.user_id = p.user_id
                WHERE p.round_id = ?
                ORDER BY p.id""", DataClassRowMapper.newInstance(PredictionSettled.class), roundId));
    }

    /** The one win rule: a prediction on {@code direction} against {@code previousRoll}, once {@code roll} is known. */
    static PredictionStatus outcome(PredictionType direction, int previousRoll, int roll) {
        if (roll == previousRoll) {
            return PredictionStatus.PUSH;
        }
        boolean higher = roll > previousRoll;
        return higher == (direction == PredictionType.HIGHER) ? PredictionStatus.WON : PredictionStatus.LOST;
    }
}
