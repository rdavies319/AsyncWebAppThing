package com.rdavies.api.repository;

import com.rdavies.api.domain.Wallet;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WalletRepository extends CrudRepository<Wallet, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE wallets SET balance = balance - :amount WHERE user_id = :userId AND balance >= :amount")
    int deductStake(@Param("userId") Long userId, @Param("amount") Long amount);

    @Modifying
    @Transactional
    @Query("UPDATE wallets SET balance = balance + :amount WHERE user_id = :userId")
    void addFunds(@Param("userId") Long userId, @Param("amount") Long amount);
}
