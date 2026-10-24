package com.rdavies.arena.wallet.records;

/**
 * One credit for {@code WalletService.creditAll}: points added to a wallet, with the ledger row that records it.
 *
 * @param refId the bet id for PAYOUT and REFUND
 */
public record Credit(long userId, long amount, LedgerEntry.Reason reason, Long refId) {
    public Credit {
        if (amount <= 0) {
            throw new IllegalArgumentException("A credit must be positive: " + amount);
        }
    }
}
