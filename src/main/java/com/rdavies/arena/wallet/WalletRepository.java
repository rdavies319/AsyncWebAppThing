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
interface WalletRepository extends CrudRepository<Wallet, Long>, WalletCredits {

    /** save() would treat a wallet with its user id already set as an update, so inserts go through here. */
    @Modifying
    @Query("INSERT INTO wallets (user_id, balance, updated_at) VALUES (:userId, :balance, :now)")
    void create(@Param("userId") Long userId, @Param("balance") long balance, @Param("now") Instant now);

    /** Highest balance first; equal balances by user id, so whoever joined first ranks first. */
    List<Wallet> findAllByOrderByBalanceDescUserIdAsc(Limit limit);

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
