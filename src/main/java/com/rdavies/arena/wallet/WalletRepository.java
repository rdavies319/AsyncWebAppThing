package com.rdavies.arena.wallet;

import com.rdavies.arena.wallet.records.Wallet;
import org.springframework.data.domain.Limit;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Package-private: balances change only through WalletService, which writes each change's ledger row with it. */
interface WalletRepository extends CrudRepository<Wallet, Long> {

    /** save() would treat a wallet with its user id already set as an update, so inserts go through here. */
    @Modifying
    @Query("INSERT INTO wallets (user_id, balance, updated_at) VALUES (:userId, :balance, :now)")
    void create(@Param("userId") Long userId, @Param("balance") long balance, @Param("now") Instant now);

    /** Highest balance first; equal balances by user id, so whoever joined first ranks first. */
    List<Wallet> findAllByOrderByBalanceDescUserIdAsc(Limit limit);

    /**
     * Writes each credit's ledger row and adds it to its wallet, in one statement, so the credits and
     * their ledger rows can't disagree. The credits come as parallel arrays (index i is one credit),
     * which Postgres unnests into rows. A wallet may take several credits: they are summed first,
     * because UPDATE ... FROM would apply only one matching row per wallet.
     *
     * @param reasons each credit's LedgerEntry.Reason name
     * @return how many wallets were credited
     */
    @Modifying
    @Query("""
            WITH credited AS (
                INSERT INTO ledger (user_id, delta, reason, ref_id, created_at)
                SELECT user_id, delta, reason, ref_id, :now
                FROM unnest(CAST(:userIds AS bigint[]), CAST(:amounts AS bigint[]), CAST(:reasons AS varchar[]),
                            CAST(:refIds AS bigint[])) AS c (user_id, delta, reason, ref_id)
                RETURNING user_id, delta)
            UPDATE wallets w SET balance = w.balance + c.delta, updated_at = :now
            FROM (SELECT user_id, SUM(delta) AS delta FROM credited GROUP BY user_id) AS c
            WHERE w.user_id = c.user_id""")
    int creditAll(@Param("userIds") Long[] userIds, @Param("amounts") Long[] amounts,
                  @Param("reasons") String[] reasons, @Param("refIds") Long[] refIds, @Param("now") Instant now);

    /** Row-locks the wallet until the caller's transaction ends. */
    @Query("SELECT * FROM wallets WHERE user_id = :userId FOR UPDATE")
    Optional<Wallet> lockById(@Param("userId") Long userId);

    /** Returns 0, changing nothing, if the balance is too low. */
    @Modifying
    @Query("UPDATE wallets SET balance = balance - :amount, updated_at = :now "
            + "WHERE user_id = :userId AND balance >= :amount")
    int deductStake(@Param("userId") Long userId, @Param("amount") Long amount, @Param("now") Instant now);

    /** Sets the balance and records today's top-up. WalletService.claimTopUp calls it holding {@link #lockById}. */
    @Modifying
    @Query("UPDATE wallets SET balance = :balance, last_top_up_date = :today, updated_at = :now WHERE user_id = :userId")
    void topUp(@Param("userId") Long userId, @Param("balance") long balance,
               @Param("today") LocalDate today, @Param("now") Instant now);
}
