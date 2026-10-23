package com.rdavies.arena.bet;

import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.TestWallets;
import com.rdavies.arena.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Placing a bet on the announced fight (spec section 5, Rules, and section 7, Placing a bet). */
@GameTest
@Transactional
class BetPlacingTests {
    private static final Instant MIDNIGHT_HALF_PAST = Instant.parse("2026-01-01T00:30:00Z");

    @Autowired BetService betService;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    long playerId;
    AnnouncedFight fight;

    @BeforeEach
    void setUp() {
        playerId = TestPlayers.signUp(userService, "punter").id();
        fight = game.boot().announced(); // even ratings: 1.96x on both corners, for 01:00
        clock.set(MIDNIGHT_HALF_PAST);
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aBetLocksInTheCornersOddsAndDebitsTheStake() {
        Bet bet = betService.place(playerId, fight.fightId(), Corner.RED, 100L);

        assertEquals(playerId, bet.userId());
        assertEquals(fight.fightId(), bet.fightId());
        assertEquals(Corner.RED, bet.corner());
        assertEquals(100, bet.stake());
        assertEquals(19600, bet.multiplierBps());
        assertEquals(BetStatus.PENDING, bet.status());
        assertNull(bet.payout());
        assertEquals(MIDNIGHT_HALF_PAST, bet.createdAt());
        assertEquals(900, walletService.getWallet(playerId).balance());
        assertEquals("STAKE:-100", jdbc.queryForObject(
                "SELECT reason || ':' || delta FROM ledger WHERE user_id = ? AND ref_id = ?",
                String.class, playerId, bet.id()));
    }

    @Test
    void bettingIsOpenUntilJustBeforeTheCutoff() {
        clock.set(Instant.parse("2026-01-01T00:59:49.999Z"));
        betService.place(playerId, fight.fightId(), Corner.BLUE, 10L);
        assertEquals(990, balance());
    }

    @Test
    void bettingClosesTenSecondsBeforeTheStart() {
        clock.set(Instant.parse("2026-01-01T00:59:50Z"));
        assertRefused(GameRuleException.Kind.CONFLICT, "Betting on this fight has closed",
                () -> betService.place(playerId, fight.fightId(), Corner.RED, 10L));
    }

    @Test
    void aFightThatHasStartedTakesNoBets() {
        game.startFight();
        assertRefused(GameRuleException.Kind.CONFLICT, "Betting on this fight has closed",
                () -> betService.place(playerId, fight.fightId(), Corner.RED, 10L));
    }

    @Test
    void aFightThatDoesNotExistTakesNoBets() {
        assertRefused(GameRuleException.Kind.CONFLICT, "Betting on this fight has closed",
                () -> betService.place(playerId, fight.fightId() + 1_000, Corner.RED, 10L));
    }

    @Test
    void aBetNeedsACorner() {
        assertRefused(GameRuleException.Kind.INVALID, "Choose a corner",
                () -> betService.place(playerId, fight.fightId(), null, 10L));
    }

    @Test
    void aBetNeedsAStake() {
        assertRefused(GameRuleException.Kind.INVALID, "Enter a stake",
                () -> betService.place(playerId, fight.fightId(), Corner.RED, null));
    }

    @Test
    void theStakeMustBeWithinTheLimits() {
        String limits = "Stake must be between 10 and 500 pts on " + fight.blue().name();
        assertRefused(GameRuleException.Kind.INVALID, limits,
                () -> betService.place(playerId, fight.fightId(), Corner.BLUE, 9L));
        assertRefused(GameRuleException.Kind.INVALID, limits,
                () -> betService.place(playerId, fight.fightId(), Corner.BLUE, 501L));

        betService.place(playerId, fight.fightId(), Corner.BLUE, 500L);
        assertEquals(500, balance());
    }

    @Test
    void oneBetPerPlayerPerFight() {
        betService.place(playerId, fight.fightId(), Corner.RED, 100L);

        for (Corner corner : Corner.values()) {
            GameRuleException e = assertThrows(GameRuleException.class,
                    () -> betService.place(playerId, fight.fightId(), corner, 50L));
            assertEquals(GameRuleException.Kind.CONFLICT, e.kind());
            assertEquals("You have already bet on this fight", e.getMessage());
        }
        assertEquals(900, balance());
    }

    @Test
    void aBetNeedsTheStakeInTheWallet() {
        wallets.spend(playerId, 950);
        GameRuleException e = assertThrows(GameRuleException.class,
                () -> betService.place(playerId, fight.fightId(), Corner.RED, 100L));
        assertEquals(GameRuleException.Kind.CONFLICT, e.kind());
        assertEquals("Insufficient funds in wallet", e.getMessage());
        assertEquals(50, balance());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bets WHERE user_id = ?", Integer.class, playerId));

        betService.place(playerId, fight.fightId(), Corner.RED, 50L); // the refusal didn't use up the one bet
        assertEquals(0, balance());
    }

    private long balance() {
        return walletService.getWallet(playerId).balance();
    }

    /** The bet is refused with this kind and message, and changes nothing. */
    private void assertRefused(GameRuleException.Kind kind, String message, Executable placing) {
        GameRuleException e = assertThrows(GameRuleException.class, placing);
        assertEquals(kind, e.kind());
        assertEquals(message, e.getMessage());
        assertEquals(1000, balance());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bets WHERE user_id = ?", Integer.class, playerId));
    }
}
