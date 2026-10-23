package com.rdavies.arena;

import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.TestWallets;
import com.rdavies.arena.wallet.WalletService;
import com.rdavies.arena.wallet.records.Balance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The top balances (spec section 9, Leaderboard). Other test classes leave wallets behind, so the
 * players here are granted far more than any of those could hold.
 */
@GameTest
@Transactional
class LeaderboardTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired WalletService walletService;
    @Autowired TestWallets wallets;

    GamePrincipal richest;
    GamePrincipal tiedFirst;
    GamePrincipal tiedSecond;

    @BeforeEach
    void signUp() {
        richest = TestPlayers.signUp(userService, "croesus");
        tiedFirst = TestPlayers.signUp(userService, "midas");
        tiedSecond = TestPlayers.signUp(userService, "crassus"); // signed up later, so ranked after midas
        wallets.grant(richest.id(), 9_000_000);
        wallets.grant(tiedFirst.id(), 5_000_000);
        wallets.grant(tiedSecond.id(), 5_000_000);
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void highestBalancesComeFirstWithTiesBrokenByWhoJoinedFirst() {
        assertEquals(List.of(
                new Balance(richest.id(), 9_001_000),
                new Balance(tiedFirst.id(), 5_001_000),
                new Balance(tiedSecond.id(), 5_001_000)), walletService.highestBalances(3));
    }

    @Test
    void theLeaderboardIsPublicJson() throws Exception {
        mockMvc.perform(get("/leaderboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].display_name").value("croesus"))
                .andExpect(jsonPath("$[0].balance").value(9_001_000))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[2].display_name").value("crassus"));
    }

    @Test
    void theLeaderboardIsAPublicPageShowingNamesNotEmails() throws Exception {
        String page = mockMvc.perform(get("/leaderboard").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("croesus"), page);
        assertTrue(page.contains("9001000"), page);
        assertFalse(page.contains("@example.invalid"), page);
    }

    @Test
    void theGamePageLinksToTheLeaderboard() throws Exception {
        String page = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("href=\"/leaderboard\""), page);
    }
}
