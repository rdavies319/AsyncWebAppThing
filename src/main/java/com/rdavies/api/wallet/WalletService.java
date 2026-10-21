package com.rdavies.api.wallet;

import com.rdavies.api.GameProperties;
import com.rdavies.api.GameRuleException;
import com.rdavies.api.wallet.records.LedgerEntry;
import com.rdavies.api.wallet.records.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Every wallet change, each with its ledger row (spec section 7): signup bonus, stakes and the daily top-up. */
@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final GameProperties game;
    private final Clock clock;

    public WalletService(WalletRepository walletRepository, LedgerRepository ledgerRepository,
                         GameProperties game, Clock clock) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.game = game;
        this.clock = clock;
    }

    public Wallet getWallet(Long userId) {
        return walletRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user"));
    }

    /** A new player's wallet, holding the signup bonus. */
    @Transactional
    public Wallet createFor(Long userId) {
        Instant now = clock.instant();
        walletRepository.create(userId, game.signupBonus(), now);
        ledgerRepository.save(new LedgerEntry(null, userId, game.signupBonus(), LedgerEntry.Reason.SIGNUP_BONUS, null, now));
        return getWallet(userId);
    }

    /**
     * Takes a bet's stake, with its ledger row. Joins the caller's transaction, so if this fails
     * the bet it pays for is rolled back too.
     */
    @Transactional
    public void debitStake(long userId, long stake, long betId, Instant now) {
        if (walletRepository.deductStake(userId, stake, now) == 0) {
            throw GameRuleException.conflict("Insufficient funds in wallet");
        }
        ledgerRepository.save(new LedgerEntry(null, userId, -stake, LedgerEntry.Reason.STAKE, betId, now));
    }

    /** Whether the daily top-up can be claimed now: balance below the target, not yet claimed today (UTC). */
    public boolean canTopUp(Wallet wallet) {
        return wallet.balance() < game.topUpTarget() && !today().equals(wallet.lastTopUpDate());
    }

    /**
     * Refills the wallet to the top-up target, once per UTC day. A refused claim (balance already at
     * the target, or already claimed today) changes nothing, so it doesn't use up the day.
     *
     * @return the points credited
     */
    @Transactional
    public long claimTopUp(Long userId) {
        Wallet wallet = walletRepository.lockById(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user"));
        if (today().equals(wallet.lastTopUpDate())) {
            throw GameRuleException.conflict("You have already claimed today's top-up");
        }
        if (wallet.balance() >= game.topUpTarget()) {
            throw GameRuleException.conflict("Top-ups are for balances below " + game.topUpTarget() + " pts");
        }
        long credit = game.topUpTarget() - wallet.balance();
        Instant now = clock.instant();
        walletRepository.topUp(userId, game.topUpTarget(), today(), now);
        ledgerRepository.save(new LedgerEntry(null, userId, credit, LedgerEntry.Reason.TOPUP, null, now));
        return credit;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
