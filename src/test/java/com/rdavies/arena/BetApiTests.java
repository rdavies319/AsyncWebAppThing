package com.rdavies.arena;

import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /bets (JSON) and POST /bet (the board's form) share BetService; BetPlacingTests covers its rules. */
@GameTest
@Transactional
class BetApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;
    @Autowired TestGame game;
    @Autowired MutableClock clock;

    GamePrincipal player;
    AnnouncedFight fight;

    @BeforeEach
    void setUp() {
        player = TestPlayers.signUp(userService, "bettor");
        fight = game.boot().announced();
        clock.now = Instant.parse("2026-01-01T00:30:00Z");
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aBetOverTheJsonApiReturnsTheStoredBet() throws Exception {
        mockMvc.perform(post("/bets").with(user(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fight_id\": " + fight.fightId() + ", \"corner\": \"blue\", \"stake\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.user_id").value(player.id()))
                .andExpect(jsonPath("$.fight_id").value(fight.fightId()))
                .andExpect(jsonPath("$.corner").value("blue"))
                .andExpect(jsonPath("$.stake").value(100))
                .andExpect(jsonPath("$.multiplier_bps").value(19600))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.payout").value(nullValue()))
                .andExpect(jsonPath("$.created_at").value("2026-01-01T00:30:00Z"));
    }

    @Test
    void anInvalidBetIsA400ProblemDetail() throws Exception {
        mockMvc.perform(post("/bets").with(user(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fight_id\": " + fight.fightId() + ", \"corner\": \"red\", \"stake\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Stake must be one of 10, 100, 500 pts on " + fight.red().name()));
    }

    @Test
    void anUnknownCornerIsA400() throws Exception {
        mockMvc.perform(post("/bets").with(user(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fight_id\": " + fight.fightId() + ", \"corner\": \"green\", \"stake\": 10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aClosedFightIsA409ProblemDetail() throws Exception {
        clock.now = Instant.parse("2026-01-01T00:59:50Z");
        mockMvc.perform(post("/bets").with(user(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fight_id\": " + fight.fightId() + ", \"corner\": \"red\", \"stake\": 10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Betting on this fight has closed"));
    }

    @Test
    void aBetNeedsALogin() throws Exception {
        mockMvc.perform(post("/bets").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fight_id\": " + fight.fightId() + ", \"corner\": \"red\", \"stake\": 10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theBoardsFormPlacesTheBetAndShowsIt() throws Exception {
        String board = postBet("red", "100");
        assertTrue(board.contains("Your bet: 100 on " + fight.red().name() + " at 1.96x"), board);
        assertTrue(board.contains("<span>900</span> pts"), board);
    }

    @Test
    void theBoardRendersAStakeButtonPerAffordableStake() throws Exception {
        String board = mockMvc.perform(get("/fragments/board").with(user(player)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.contains("Bet on " + fight.red().name()), board);
        assertTrue(board.contains("name=\"corner\" value=\"blue\""), board);
        assertTrue(board.contains("name=\"stake\" value=\"500\""), board);
    }

    @Test
    void theBoardsFormShowsARefusalInline() throws Exception {
        postBet("red", "100");
        String board = postBet("blue", "10");
        assertTrue(board.contains("You have already bet on this fight"), board);
    }

    private String postBet(String corner, String stake) throws Exception {
        return mockMvc.perform(post("/bet").with(user(player)).with(csrf())
                        .param("fight_id", String.valueOf(fight.fightId()))
                        .param("corner", corner)
                        .param("stake", stake))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
