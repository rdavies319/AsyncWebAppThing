package com.rdavies.api;

import com.rdavies.api.board.BoardService;
import com.rdavies.api.board.records.Board;
import com.rdavies.api.user.UserService;
import com.rdavies.api.user.records.GamePrincipal;
import com.rdavies.api.wallet.TestWallets;
import com.rdavies.api.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** What each viewer sees on the game page, checked on the Board value rather than the rendered HTML. */
@GameTest
@Transactional
class BoardTests {
    @Autowired BoardService boardService;
    @Autowired UserService userService;
    @Autowired WalletService walletService;
    @Autowired TestWallets wallets;
    @Autowired MockMvc mockMvc;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void anAnonymousViewerSeesNothingPersonal() {
        assertNull(boardService.board(Optional.empty()).player());
    }

    @Test
    void aPlayerSeesTheirBalance() {
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");

        Board board = boardService.board(Optional.of(alice));

        assertEquals(new Board.PlayerBoard("alice", 1000, null), board.player()); // at the top-up target already
    }

    @Test
    void aPlayerBelowTheTargetIsOfferedTheTopUpUntilTheyClaimIt() {
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");
        wallets.spend(alice.id(), 400);

        assertEquals(new Board.PlayerBoard("alice", 600, 1000L), boardService.board(Optional.of(alice)).player());

        walletService.claimTopUp(alice.id());
        assertEquals(new Board.PlayerBoard("alice", 1000, null), boardService.board(Optional.of(alice)).player());
    }

    @Test
    void everyoneSeesTheArenaIsOpeningSoon() throws Exception {
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");
        String anonymous = mockMvc.perform(get("/fragments/board")).andReturn().getResponse().getContentAsString();
        String player = mockMvc.perform(get("/fragments/board").with(user(alice))).andReturn().getResponse().getContentAsString();

        assertTrue(anonymous.contains("Arena opening soon"), anonymous);
        assertTrue(player.contains("Arena opening soon"), player);
        assertTrue(player.contains("Balance: <span>1000</span> pts"), player);
    }
}
