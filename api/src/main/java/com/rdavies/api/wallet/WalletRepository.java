package com.rdavies.api.wallet;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface WalletRepository extends CrudRepository<Wallet, Long> {

    /** save() would treat a wallet with its user id already set as an update, so inserts go through here. */
    @Modifying
    @Query("INSERT INTO wallets (user_id, balance, updated_at) VALUES (:userId, :balance, :now)")
    void create(@Param("userId") Long userId, @Param("balance") long balance, @Param("now") Instant now);

    /** Row-locks the wallet until the caller's transaction ends. */
    @Query("SELECT * FROM wallets WHERE user_id = :userId FOR UPDATE")
    Optional<Wallet> lockById(@Param("userId") Long userId);

    /** Returns 0, changing nothing, if the balance is too low. */
    @Modifying
    @Query("UPDATE wallets SET balance = balance - :amount, updated_at = :now "
            + "WHERE user_id = :userId AND balance >= :amount")
    int deductStake(@Param("userId") Long userId, @Param("amount") Long amount, @Param("now") Instant now);

    /** Call only while holding {@link #lockById}: sets the balance and records today's top-up. */
    @Modifying
    @Query("UPDATE wallets SET balance = :balance, last_top_up_date = :today, updated_at = :now WHERE user_id = :userId")
    void topUp(@Param("userId") Long userId, @Param("balance") long balance,
               @Param("today") LocalDate today, @Param("now") Instant now);

    /**
     * Credits every settled prediction on the round that paid out (wins and pushes), with one PAYOUT
     * or REFUND ledger row each. One statement, so the credits and the ledger rows can't disagree.
     * A player has at most one prediction per round, so each wallet is credited at most once.
     */
    @Modifying
    @Query("""
            WITH paid AS (
                INSERT INTO ledger (user_id, delta, reason, ref_id, created_at)
                SELECT user_id, payout, CASE status WHEN 'WON' THEN 'PAYOUT' ELSE 'REFUND' END, id, :now
                FROM predictions
                WHERE round_id = :roundId AND payout > 0
                RETURNING user_id, delta)
            UPDATE wallets w SET balance = w.balance + paid.delta, updated_at = :now
            FROM paid
            WHERE paid.user_id = w.user_id
            """)
    int creditRoundPayouts(@Param("roundId") Long roundId, @Param("now") Instant now);
}
