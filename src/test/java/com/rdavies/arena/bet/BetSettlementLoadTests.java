package com.rdavies.arena.bet;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settling 1,000 bets takes under 2 seconds (spec section 7, Timing targets). */
@GameTest
@Transactional
class BetSettlementLoadTests {
    private static final int BETS = 1_000;
    private static final long STAKE = 100;

    @Autowired TestGame game;
    @Autowired TestWallets wallets;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aThousandBetsSettleInUnderTwoSeconds() {
        game.plantFight(Winner.RED, EndedBy.KO);
        long fightId = game.boot().announced().fightId();
        placeBulkBets(fightId); // half on each corner, 1.96x

        LiveFight live = game.startFight();
        CurrentFights now;
        long settlingTick;
        do { // the tick that runs the last round settles the fight and its bets
            long start = System.nanoTime();
            now = game.nextRound();
            settlingTick = System.nanoTime() - start;
        } while (now.live() != null);

        assertTrue(Duration.ofNanos(settlingTick).compareTo(Duration.ofSeconds(2)) < 0,
                "settling took " + Duration.ofNanos(settlingTick));
        assertEquals(BETS / 2, count(live.fightId(), "WON"));
        assertEquals(BETS / 2, count(live.fightId(), "LOST"));
        assertEquals(BETS / 2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE reason = 'PAYOUT' AND ref_id IN (SELECT id FROM bets WHERE fight_id = ?)",
                Integer.class, live.fightId()));
    }

    /** 1,000 players, each with one pending bet and its stake taken, written in bulk rather than bet by bet. */
    private void placeBulkBets(long fightId) {
        jdbc.update("""
                INSERT INTO users (email, display_name, password_hash)
                SELECT 'load' || n || '@example.invalid', 'load' || n, '!' FROM generate_series(1, ?) AS n""", BETS);
        wallets.openWalletsForUsers("load%@example.invalid", 1000);
        wallets.spendForUsers("load%@example.invalid", STAKE);
        jdbc.update("""
                INSERT INTO bets (user_id, fight_id, corner, stake, multiplier_bps, status, created_at)
                SELECT id, ?, CASE WHEN id % 2 = 0 THEN 'RED' ELSE 'BLUE' END, ?, 19600, 'PENDING', now()
                FROM users WHERE email LIKE 'load%@example.invalid'""", fightId, STAKE);
    }

    private int count(long fightId, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bets WHERE fight_id = ? AND status = ?",
                Integer.class, fightId, status);
    }
}
