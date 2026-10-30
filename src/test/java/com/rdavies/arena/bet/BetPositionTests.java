package com.rdavies.arena.bet;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.bet.records.BetPosition;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a player can do on the announced fight: their bet, the stakes open to them, or nothing once betting closes. */
@GameTest
@Transactional
class BetPositionTests {
    @Autowired BetService betService;
    @Autowired FightService fightService;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;
    @Autowired TestGame game;
    @Autowired MutableClock clock;

    long playerId;
    AnnouncedFight fight;

    @BeforeEach
    void setUp() {
        playerId = TestPlayers.signUp(userService, "positioned").id();
        fight = game.boot().announced(); // even ratings, for 01:00; the player has 1,000
        clock.set(Instant.parse("2026-01-01T00:30:00Z"));
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aPlayerWhoCanAffordEveryStakeHasThemAllOpenOnBothCorners() {
        assertEquals(new BetPosition.Open(List.of(10L, 100L, 500L), List.of(10L, 100L, 500L)),
                betService.position(playerId, fight));
    }

    @Test
    void theBalanceLeavesOnlyTheStakesItCovers() {
        wallets.spend(playerId, 900);
        assertEquals(new BetPosition.Open(List.of(10L, 100L), List.of(10L, 100L)), betService.position(playerId, fight));

        wallets.spend(playerId, 91);
        assertEquals(new BetPosition.Open(List.of(), List.of()), betService.position(playerId, fight)); // 9 left: still open
    }

    @Test
    void eachCornerOffersOnlyItsOwnStakes() {
        // As the payout cap would leave a long shot: the fight decides each corner's stakes
        AnnouncedFight capped = new AnnouncedFight(fight.fightId(), fight.startsAt(), fight.cutoffAt(), true,
                fight.red(), withStakes(fight.blue(), List.of(10L, 100L)));

        assertEquals(new BetPosition.Open(List.of(10L, 100L, 500L), List.of(10L, 100L)),
                betService.position(playerId, capped));
    }

    @Test
    void onceTheyBetThePlayerHasTheirBet() {
        Bet bet = betService.place(playerId, fight.fightId(), Corner.BLUE, 100L);

        assertEquals(new BetPosition.Placed(bet), betService.position(playerId, fight));
    }

    @Test
    void theirBetStaysAfterBettingCloses() {
        Bet bet = betService.place(playerId, fight.fightId(), Corner.RED, 10L);
        clock.set(Instant.parse("2026-01-01T00:59:50Z"));

        assertEquals(new BetPosition.Placed(bet), betService.position(playerId, fightService.current().announced()));
    }

    @Test
    void atTheCutoffBettingClosesForAPlayerWithNoBet() {
        clock.set(Instant.parse("2026-01-01T00:59:50Z"));

        assertEquals(new BetPosition.Closed(), betService.position(playerId, fightService.current().announced()));
    }

    private static AnnouncedFight.Fighter withStakes(AnnouncedFight.Fighter f, List<Long> stakes) {
        return new AnnouncedFight.Fighter(f.slug(), f.name(), f.epithet(), f.elo(), f.multiplierBps(), stakes);
    }
}
