package com.rdavies.arena.wallet;

import com.rdavies.arena.wallet.records.Credit;

import java.time.Instant;
import java.util.List;

/** A WalletRepository fragment for the batch credit, which passes arrays and so needs raw JDBC. */
interface WalletCredits {

    /**
     * Writes every credit's ledger row and adds it to its wallet, in one statement, so the credits and
     * their ledger rows can't disagree. A wallet may take several credits.
     *
     * @return how many wallets were credited
     */
    int creditAll(List<Credit> credits, Instant now);
}
