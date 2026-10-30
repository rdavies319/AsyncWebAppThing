package com.rdavies.arena;

import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.board.BoardService;
import com.rdavies.arena.board.records.Board;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A player's own bets: GET /bets/me, and the last 5 on the board. */
@GameTest
@Transactional
class BetHistoryTests {
    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired BetService betService;
    @Autowired FightService fightService;
    @Autowired BoardService boardService;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;

    GamePrincipal player;
    GamePrincipal rival;

    @BeforeEach
    void signUp() {
        player = TestPlayers.signUp(userService, "historian");
        rival = TestPlayers.signUp(userService, "rival");
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void betHistoryNeedsALogin() throws Exception {
        mockMvc.perform(get("/bets/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void betHistoryListsThePlayersBetsNewestFirst() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        AnnouncedFight first = announced();
        bet(player, Corner.RED, 100);
        game.finishFight();
        AnnouncedFight second = announced();
        bet(player, Corner.BLUE, 10); // pending

        mockMvc.perform(get("/bets/me").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fight_id").value(second.fightId()))
                .andExpect(jsonPath("$[0].red").value(second.red().name()))
                .andExpect(jsonPath("$[0].blue").value(second.blue().name()))
                .andExpect(jsonPath("$[0].corner").value("blue"))
                .andExpect(jsonPath("$[0].stake").value(10))
                .andExpect(jsonPath("$[0].status").value("pending"))
                .andExpect(jsonPath("$[0].payout").value(nullValue()))
                .andExpect(jsonPath("$[1].fight_id").value(first.fightId()))
                .andExpect(jsonPath("$[1].starts_at").value("2026-01-01T01:00:00Z"))
                .andExpect(jsonPath("$[1].multiplier_bps").value(19600))
                .andExpect(jsonPath("$[1].status").value("won"))
                .andExpect(jsonPath("$[1].payout").value(196));
    }

    @Test
    void aPlayerNeverSeesAnotherPlayersBets() throws Exception {
        game.boot();
        bet(rival, Corner.RED, 100);

        mockMvc.perform(get("/bets/me").with(user(player))).andExpect(jsonPath("$", hasSize(0)));
        assertEquals(List.of(), boardService.board(Optional.of(player)).player().recentBets());
        mockMvc.perform(get("/bets/me").with(user(rival))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void theBoardShowsTheLastFiveBetsLinkedToTheirFights() throws Exception {
        List<Bet> placed = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                game.finishFight();
            }
            placed.add(bet(player, Corner.RED, 10));
        }

        List<Board.PastBet> recent = boardService.board(Optional.of(player)).player().recentBets();

        assertEquals(placed.reversed().subList(0, 5).stream().map(Bet::fightId).toList(),
                recent.stream().map(Board.PastBet::fightId).toList());
        AnnouncedFight newest = fightService.current().announced();
        assertEquals(newest.red().name() + " v " + newest.blue().name() + ": 10 on " + newest.red().name()
                + " at " + Wording.odds(newest.red().multiplierBps()) + " · pending", recent.getFirst().line()); // ratings have moved
        String board = mockMvc.perform(get("/fragments/board").with(user(player)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.contains("href=\"/fights/" + placed.get(1).fightId() + "\""), board);
    }

    @Test
    void settledBetsReadTheirResult() {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        AnnouncedFight fight = announced();
        bet(player, Corner.RED, 100);
        game.finishFight();

        assertEquals(fight.red().name() + " v " + fight.blue().name() + ": 100 on " + fight.red().name()
                + " at 1.96x · lost", boardService.board(Optional.of(player)).player().recentBets().getFirst().line());
    }

    private AnnouncedFight announced() {
        if (fightService.current().announced() == null) {
            game.boot();
        }
        return fightService.current().announced();
    }

    /** A bet on the announced fight, placed half an hour before its start. */
    private Bet bet(GamePrincipal who, Corner corner, long stake) {
        AnnouncedFight fight = announced();
        clock.now = fight.startsAt().minusSeconds(30 * 60);
        return betService.place(who.id(), fight.fightId(), corner, stake);
    }
}
