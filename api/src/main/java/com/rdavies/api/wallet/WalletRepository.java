package com.rdavies.api.wallet;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface WalletRepository extends CrudRepository<Wallet, Long> {

    /** Returns 0, changing nothing, if the balance is too low. */
    @Modifying
    @Query("UPDATE wallets SET balance = balance - :amount, updated_at = :now "
            + "WHERE user_id = :userId AND balance >= :amount")
    int deductStake(@Param("userId") Long userId, @Param("amount") Long amount, @Param("now") Instant now);

    /** Credits every settled prediction on the round that paid out (wins and pushes). */
    @Modifying
    @Query("""
            UPDATE wallets w SET balance = w.balance + p.payout, updated_at = :now
            FROM predictions p
            WHERE p.round_id = :roundId AND p.user_id = w.user_id AND p.payout > 0
            """)
    int creditRoundPayouts(@Param("roundId") Long roundId, @Param("now") Instant now);
}
