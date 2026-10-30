package com.rdavies.arena.bet;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.TestWallets;
import com.rdavies.arena.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Settling a fight's bets when the engine settles the fight (spec section 5, Rules, and section 7, step 4). */
@GameTest
@Transactional
class BetSettlementTests {
    private static final Instant HALF_PAST_MIDNIGHT = Instant.parse("2026-01-01T00:30:00Z");

    @Autowired BetService betService;
    @Autowired BetSettlement settlement;
    @Autowired FightService fightService;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    long redBacker;
    long blueBacker;

    @BeforeEach
    void signUp() {
        redBacker = TestPlayers.signUp(userService, "red_backer").id();
        blueBacker = TestPlayers.signUp(userService, "blue_backer").id();
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aWinPaysTheStakeTimesTheOddsAndALossPaysNothing() {
        game.plantFight(Winner.RED, EndedBy.KO);
        Bet onRed = bet(redBacker, Corner.RED, 10);
        Bet onBlue = bet(blueBacker, Corner.BLUE, 100);

        FightResult result = game.finishFight();

        Bet won = betService.bet(redBacker, result.fightId()).orElseThrow();
        assertEquals(BetStatus.WON, won.status());
        assertEquals(19, won.payout()); // floor(10 × 19600 / 10000) = floor(19.6)
        assertEquals(1000 - 10 + 19, balance(redBacker));
        assertEquals(List.of("STAKE:-10", "PAYOUT:19"), ledger(redBacker, onRed.id()));

        Bet lost = betService.bet(blueBacker, result.fightId()).orElseThrow();
        assertEquals(BetStatus.LOST, lost.status());
        assertEquals(0, lost.payout());
        assertEquals(900, balance(blueBacker));
        assertEquals(List.of("STAKE:-100"), ledger(blueBacker, onBlue.id()));
        assertEquals(clock.instant(), won.settledAt());
    }

    @Test
    void aDrawRefundsEveryStake() {
        game.plantFight(Winner.DRAW, EndedBy.DOUBLE_KO);
        Bet onRed = bet(redBacker, Corner.RED, 500);
        Bet onBlue = bet(blueBacker, Corner.BLUE, 10);

        FightResult result = game.finishFight();

        for (Bet placed : List.of(onRed, onBlue)) {
            Bet settled = betService.bet(placed.userId(), result.fightId()).orElseThrow();
            assertEquals(BetStatus.PUSH, settled.status());
            assertEquals(placed.stake(), settled.payout());
            assertEquals(1000, balance(placed.userId()));
            assertEquals(List.of("STAKE:" + -placed.stake(), "REFUND:" + placed.stake()), ledger(placed.userId(), placed.id()));
        }
    }

    @Test
    void aFightSettlesItsBetsOnlyOnce() {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        bet(blueBacker, Corner.BLUE, 100);
        FightResult result = game.finishFight();
        assertEquals(1096, balance(blueBacker));

        settlement.on(new FightSettled(result.fightId(), Winner.BLUE, EndedBy.KO, 1000, 1000)); // delivered again
        game.tickAt(clock.instant().plusSeconds(60));

        assertEquals(1096, balance(blueBacker));
    }

    @Test
    void betsOnOtherFightsAreLeftAlone() {
        game.plantFight(Winner.RED, EndedBy.KO);
        game.startFight(); // announces the 02:00 fight
        clock.set(Instant.parse("2026-01-01T01:00:30Z"));
        Bet onNext = betService.place(redBacker, fightService.current().announced().fightId(), Corner.RED, 100L);

        game.finishFight();

        assertEquals(BetStatus.PENDING, betService.bet(redBacker, onNext.fightId()).orElseThrow().status());
        assertEquals(900, balance(redBacker));
    }

    private Bet bet(long userId, Corner corner, long stake) {
        clock.set(HALF_PAST_MIDNIGHT);
        return betService.place(userId, fightService.current().announced().fightId(), corner, stake);
    }

    private long balance(long userId) {
        return walletService.getWallet(userId).balance();
    }

    /** The bet's ledger rows, oldest first, as "REASON:delta". */
    private List<String> ledger(long userId, long betId) {
        return jdbc.queryForList("SELECT reason || ':' || delta FROM ledger WHERE user_id = ? AND ref_id = ? ORDER BY id",
                String.class, userId, betId);
    }
}
