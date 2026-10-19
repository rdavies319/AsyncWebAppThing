package com.rdavies.api;

import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.TestWallets;
import com.rdavies.api.wallet.Wallet;
import com.rdavies.api.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signup bonus and daily top-up (spec section 3). */
@GameTest
@Transactional
class WalletTests {
    private GamePrincipal player;
    private long playerId;
    private static final Instant MORNING = Instant.parse("2026-01-01T09:00:00Z");

    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;
    @Autowired TestWallets wallets;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @BeforeEach
    void setUp() {
        player = TestPlayers.signUp(userService, "player");
        playerId = player.id();
        clock.now = MORNING;
    }

    @Test
    void aNewWalletHoldsTheSignupBonus() {
        long userId = jdbc.queryForObject("""
                INSERT INTO users (email, display_name, password_hash)
                VALUES ('new@example.invalid', 'newcomer', '!') RETURNING id""", Long.class);

        Wallet wallet = walletService.createFor(userId);

        assertEquals(1000, wallet.balance());
        assertNull(wallet.lastTopUpDate());
        assertEquals("SIGNUP_BONUS", jdbc.queryForObject("SELECT reason FROM ledger WHERE user_id = ?", String.class, userId));
        game.assertLedgerMatchesWallet(userId);
    }

    @Test
    void topUpRefillsToTheTarget() {
        spend(700);

        assertEquals(700, walletService.claimTopUp(playerId));

        assertEquals(1000, balance());
        assertEquals(700, jdbc.queryForObject(
                "SELECT delta FROM ledger WHERE user_id = ? AND reason = 'TOPUP'", Long.class, playerId));
        game.assertLedgerMatchesWallet(playerId);
    }

    @Test
    void topUpIsOncePerUtcDay() {
        spend(500);
        walletService.claimTopUp(playerId);
        spend(500);

        clock.now = Instant.parse("2026-01-01T23:59:59Z");
        assertThrows(GameRuleException.class, () -> walletService.claimTopUp(playerId));
        assertEquals(500, balance());

        clock.now = Instant.parse("2026-01-02T00:00:00Z");
        assertEquals(500, walletService.claimTopUp(playerId));
        game.assertLedgerMatchesWallet(playerId);
    }

    @Test
    void aRefusedClaimDoesNotUseUpTheDay() {
        assertThrows(GameRuleException.class, () -> walletService.claimTopUp(playerId)); // already at 1000
        spend(100);
        assertEquals(100, walletService.claimTopUp(playerId));
    }

    @Test
    void canTopUpOnlyWhenBelowTargetAndNotClaimedToday() {
        assertFalse(walletService.canTopUp(wallet()));
        spend(1);
        assertTrue(walletService.canTopUp(wallet()));
        walletService.claimTopUp(playerId);
        spend(1);
        assertFalse(walletService.canTopUp(wallet()));
    }

    @Test
    void topUpOverTheJsonApi() throws Exception {
        spend(250);
        mockMvc.perform(post("/wallet/topup").with(user(player)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credited").value(250))
                .andExpect(jsonPath("$.balance").value(1000));
        mockMvc.perform(post("/wallet/topup").with(user(player)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have already claimed today's top-up"));
    }

    @Test
    void theBoardOffersTheTopUpOnlyWhenItCanBeClaimed() throws Exception {
        assertFalse(board().contains("Daily top-up"));
        spend(400);
        assertTrue(board().contains("Daily top-up to 1000"));

        String afterClaim = mockMvc.perform(post("/topup").with(user(player)).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(afterClaim.contains("<span>1000</span> pts"), afterClaim);
        assertFalse(afterClaim.contains("Daily top-up"), afterClaim);
    }

    private String board() throws Exception {
        return mockMvc.perform(get("/fragments/board").with(user(player))).andReturn().getResponse().getContentAsString();
    }

    /** Takes points from the player's wallet with a matching ledger row, as a lost stake would. */
    private void spend(long points) {
        wallets.spend(playerId, points);
    }

    private Wallet wallet() {
        return walletService.getWallet(playerId);
    }

    private long balance() {
        return wallet().balance();
    }
}
