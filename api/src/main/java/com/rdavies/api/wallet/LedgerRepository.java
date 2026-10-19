package com.rdavies.api.wallet;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LedgerRepository extends CrudRepository<LedgerEntry, Long> {

    /** One PAYOUT or REFUND row per settled prediction on the round that paid out; mirrors creditRoundPayouts. */
    @Modifying
    @Query("""
            INSERT INTO ledger (user_id, delta, reason, ref_id, created_at)
            SELECT user_id, payout, CASE status WHEN 'WON' THEN 'PAYOUT' ELSE 'REFUND' END, id, :now
            FROM predictions
            WHERE round_id = :roundId AND payout > 0
            """)
    int recordRoundPayouts(@Param("roundId") Long roundId, @Param("now") Instant now);
}
