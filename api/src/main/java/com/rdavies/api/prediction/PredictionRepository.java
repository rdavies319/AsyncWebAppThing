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
     * Settles every pending prediction on a round in one statement, given each direction's outcome
     * (decided by Settlement). A win pays floor(stake * multiplier_bps / 10000), stake included; a
     * push refunds the stake; a loss pays 0 (the stake was debited when placed). Use Settlement.settle.
     */
    @Modifying
    @Query("""
            UPDATE predictions p SET
                status = o.status,
                payout = CASE o.status
                    WHEN 'WON' THEN p.stake * p.multiplier_bps / 10000
                    WHEN 'PUSH' THEN p.stake
                    ELSE 0 END,
                settled_at = :now
            FROM (VALUES ('HIGHER', :higher), ('LOWER', :lower)) AS o (direction, status)
            WHERE p.round_id = :roundId AND p.status = 'PENDING' AND p.direction = o.direction
            """)
    int settleRound(@Param("roundId") Long roundId, @Param("higher") String higherOutcome,
                    @Param("lower") String lowerOutcome, @Param("now") Instant now);
}
