package com.rdavies.arena;

import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.board.BoardService;
import com.rdavies.arena.board.records.Board;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.TestWallets;
import com.rdavies.arena.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * What each viewer sees on the game page, checked on the Board value rather than the rendered HTML.
 * Every fight state is checked for a logged-out and a logged-in viewer, who see the same arena.
 */
@GameTest
@Transactional
class BoardTests {
    private static final Instant ONE_AM = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant TWO_AM = Instant.parse("2026-01-01T02:00:00Z");

    @Autowired BoardService boardService;
    @Autowired BetService betService;
    @Autowired UserService userService;
    @Autowired WalletService walletService;
    @Autowired TestWallets wallets;
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;

    GamePrincipal alice;

    @BeforeEach
    void signUp() {
        alice = TestPlayers.signUp(userService, "alice");
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void beforeAnythingIsAnnouncedTheArenaIsEmpty() {
        clock.now = TestGame.BOOT;

        Board board = arena();

        assertEquals(TestGame.BOOT, board.serverTime());
        assertTrue(board.openingSoon());
        assertNull(board.live());
        assertNull(board.lastResult());
        assertNull(board.next());
    }

    @Test
    void beforeTheFirstFightOnlyTheNextMatchupShows() {
        AnnouncedFight announced = game.boot().announced();

        Board board = arena();

        assertFalse(board.openingSoon());
        assertNull(board.live());
        assertNull(board.lastResult());
        assertEquals(new Board.Next(announced.fightId(),
                new Board.Contender(announced.red().name(), announced.red().epithet(), 1000, 19600, 500),
                new Board.Contender(announced.blue().name(), announced.blue().epithet(), 1000, 19600, 500),
                ONE_AM, Instant.parse("2026-01-01T00:59:50Z"), true), board.next());
    }

    @Test
    void oddsShowAsAMultiplierRoundedDown() {
        assertEquals("1.96x", new Board.Contender("Vex", "the Quick", 1000, 19600, 500).odds());
        assertEquals("1.08x", new Board.Contender("Vex", "the Quick", 1383, 10888, 500).odds()); // pays 1.0888x
        assertEquals("9.80x", new Board.Contender("Vex", "the Quick", 617, 98000, 500).odds());
    }

    @Test
    void aLiveFightShowsBothCornersAndTheNextRound() {
        LiveFight started = game.startFight();
        LiveFight live = game.nextRound().live();

        Board board = arena();

        assertEquals(Instant.parse("2026-01-01T01:01:00Z"), board.serverTime()); // when the first round ran
        assertEquals(started.fightId(), board.live().fightId());
        assertEquals(new Board.Corner(live.red().name(), live.red().epithet(), live.red().startingHp(), live.red().currentHp()),
                board.live().red());
        assertEquals(new Board.Corner(live.blue().name(), live.blue().epithet(), live.blue().startingHp(), live.blue().currentHp()),
                board.live().blue());
        assertEquals(ONE_AM.plusSeconds(120), board.live().nextRoundAt());
        assertNull(board.lastResult());
        assertEquals(TWO_AM, board.next().startsAt()); // announced when this fight started
    }

    @Test
    void theRoundLogReadsNewestFirst() {
        game.plantFight(Winner.RED, EndedBy.KO); // lasts more than two rounds
        LiveFight live = game.startFight();
        game.nextRound();
        LiveFight afterTwo = game.nextRound().live();

        Board board = arena();

        String red = live.red().name();
        String blue = live.blue().name();
        assertEquals(2, board.live().log().size());
        assertEquals(logLine(afterTwo.rounds().get(1), red, blue), board.live().log().get(0));
        assertEquals(logLine(afterTwo.rounds().get(0), red, blue), board.live().log().get(1));
    }

    @Test
    void hpBarsAreAShareOfStartingHpAndNeverNegative() {
        assertEquals(50, new Board.Corner("Brutus", "the Anvil", 120, 60).hpPercent());
        assertEquals(0, new Board.Corner("Brutus", "the Anvil", 120, 0).hpPercent());
        assertEquals(100, new Board.Corner("Brutus", "the Anvil", 120, 120).hpPercent());
        assertEquals(0, new Board.Corner("Brutus", "the Anvil", 120, -7).hpPercent()); // knocked out
        assertEquals(0, new Board.Corner("Brutus", "the Anvil", 120, -7).shownHp());
    }

    @Test
    void afterAWinTheLastResultShowsUntilTheNextFight() {
        game.plantFight(Winner.RED, EndedBy.KO);
        LiveFight live = game.startFight();
        FightResult result = game.finishFight();

        Board board = arena();

        assertNull(board.live());
        assertEquals(new Board.Result(result.fightId(),
                live.red().name() + " beat " + live.blue().name() + " by KO"), board.lastResult());
        assertEquals(TWO_AM, board.next().startsAt());
    }

    @Test
    void aDoubleKnockoutIsADraw() {
        game.plantFight(Winner.DRAW, EndedBy.DOUBLE_KO);
        LiveFight live = game.startFight();
        FightResult result = game.finishFight();

        Board board = arena();

        assertEquals(new Board.Result(result.fightId(),
                "Draw: " + live.red().name() + " and " + live.blue().name() + " knocked each other out"),
                board.lastResult());
    }

    @Test
    void theLastResultGivesWayToTheNextFight() {
        game.plantFight(Winner.RED, EndedBy.KO);
        game.finishFight();

        LiveFight second = game.startFight();
        Board board = arena();

        assertNull(board.lastResult());
        assertEquals(second.fightId(), board.live().fightId());
    }

    @Test
    void theDevPanelIsHiddenWithoutTheDevProfile() {
        assertFalse(boardService.board(Optional.of(alice)).devPanel());
        assertFalse(boardService.board(Optional.empty()).devPanel());
    }

    @Test
    void anAnonymousViewerSeesNothingPersonal() {
        assertNull(boardService.board(Optional.empty()).player());
    }

    @Test
    void aPlayerSeesTheirBalance() {
        Board board = boardService.board(Optional.of(alice));

        assertEquals(new Board.PlayerBoard("alice", 1000, null, null, null, null), board.player()); // at the top-up target already
    }

    @Test
    void aPlayerBelowTheTargetIsOfferedTheTopUpUntilTheyClaimIt() {
        wallets.spend(alice.id(), 400);

        assertEquals(new Board.PlayerBoard("alice", 600, 1000L, null, null, null), boardService.board(Optional.of(alice)).player());

        walletService.claimTopUp(alice.id());
        assertEquals(new Board.PlayerBoard("alice", 1000, null, null, null, null), boardService.board(Optional.of(alice)).player());
    }

    @Test
    void aPlayerIsOfferedTheStakesTheyCanAffordOnEachCorner() {
        long fightId = game.boot().announced().fightId();

        assertEquals(new Board.BetForm(fightId, List.of(10L, 100L, 500L), List.of(10L, 100L, 500L)),
                boardService.board(Optional.of(alice)).player().betForm());

        wallets.spend(alice.id(), 850);
        assertEquals(new Board.BetForm(fightId, List.of(10L, 100L), List.of(10L, 100L)),
                boardService.board(Optional.of(alice)).player().betForm());

        wallets.spend(alice.id(), 145);
        Board.BetForm broke = boardService.board(Optional.of(alice)).player().betForm();
        assertTrue(broke.nothingAffordable());
    }

    @Test
    void onceTheyBetAPlayerSeesTheirBetInsteadOfTheForm() {
        AnnouncedFight announced = game.boot().announced();
        betService.place(alice.id(), announced.fightId(), Corner.BLUE, 100L);

        Board.PlayerBoard me = boardService.board(Optional.of(alice)).player();

        assertNull(me.betForm());
        assertEquals("Your bet: 100 on " + announced.blue().name() + " at 1.96x", me.nextBet());
        assertEquals(900, me.balance());
    }

    @Test
    void theFormGoesOnceBettingCloses() {
        game.boot();
        clock.now = Instant.parse("2026-01-01T00:59:50Z");

        Board board = arena();

        assertFalse(board.next().bettingOpen());
        assertNull(boardService.board(Optional.of(alice)).player().betForm());
    }

    @Test
    void theBoardRefetchesOnEveryArenaEvent() throws Exception {
        String page = mockMvc.perform(get("/").with(user(alice))).andReturn().getResponse().getContentAsString();

        assertTrue(page.contains(
                "hx-trigger=\"sse:snapshot, sse:matchup_announced, sse:fight_started, sse:round, sse:fight_settled\""),
                page);
    }

    @Test
    void theBoardRendersTheLiveFightForEveryone() throws Exception {
        LiveFight live = game.startFight();
        String anonymous = mockMvc.perform(get("/fragments/board")).andReturn().getResponse().getContentAsString();
        String player = mockMvc.perform(get("/fragments/board").with(user(alice))).andReturn().getResponse().getContentAsString();

        for (String html : new String[] {anonymous, player}) {
            assertTrue(html.contains(live.red().name()), html);
            assertTrue(html.contains(live.blue().name()), html);
            assertTrue(html.contains("data-until=\"2026-01-01T01:01:00Z\""), html); // the next round's countdown
        }
        assertTrue(player.contains("Balance: <span>1000</span> pts"), player);
    }

    /**
     * The board as both viewers see it, after checking they see the same arena and that only the
     * logged-in one gets a player part.
     */
    private Board arena() {
        Board anonymous = boardService.board(Optional.empty());
        Board player = boardService.board(Optional.of(alice));
        assertNull(anonymous.player());
        assertNotNull(player.player());
        assertEquals(anonymous.serverTime(), player.serverTime());
        assertEquals(anonymous.live(), player.live());
        assertEquals(anonymous.lastResult(), player.lastResult());
        assertEquals(anonymous.next(), player.next());
        return anonymous;
    }

    /** "2. Brutus hits Vex for 14 · Brutus dodges": red's attack, then blue's. */
    private static String logLine(RoundView e, String red, String blue) {
        String redAttack = e.redHit() ? red + " hits " + blue + " for " + e.redDamage() : blue + " dodges";
        String blueAttack = e.blueHit() ? blue + " hits " + red + " for " + e.blueDamage() : red + " dodges";
        return e.n() + ". " + redAttack + " · " + blueAttack;
    }
}
