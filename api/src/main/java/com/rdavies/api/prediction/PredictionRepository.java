package com.rdavies.api.prediction;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PredictionRepository extends CrudRepository<Prediction, Long> {
    boolean existsByRoundIdAndUserId(Long roundId, Long userId);

    Optional<Prediction> findByRoundIdAndUserId(Long roundId, Long userId);

    /**
     * Settles every pending prediction on a round in one statement. {@code previous} is the round's
     * roll and {@code roll} the next round's. A win pays floor(stake * multiplier_bps / 10000), stake
     * included; a push refunds the stake; a loss pays 0 (the stake was debited when placed).
     */
    @Modifying
    @Query("""
            UPDATE predictions SET
                status = CASE
                    WHEN :roll = :previous THEN 'PUSH'
                    WHEN (direction = 'HIGHER' AND :roll > :previous)
                      OR (direction = 'LOWER' AND :roll < :previous) THEN 'WON'
                    ELSE 'LOST' END,
                payout = CASE
                    WHEN :roll = :previous THEN stake
                    WHEN (direction = 'HIGHER' AND :roll > :previous)
                      OR (direction = 'LOWER' AND :roll < :previous) THEN stake * multiplier_bps / 10000
                    ELSE 0 END,
                settled_at = :now
            WHERE round_id = :roundId AND status = 'PENDING'
            """)
    int settleRound(@Param("roundId") Long roundId, @Param("previous") int previous,
                    @Param("roll") int roll, @Param("now") Instant now);
}
