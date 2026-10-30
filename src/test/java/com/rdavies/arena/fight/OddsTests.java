package com.rdavies.arena.fight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixed odds from public Elo, from spec section 5 (Odds). */
class OddsTests {

    @Test
    void equalRatingsAreEvenMoneyLessTheEdge() {
        assertEquals(5000, Odds.redChanceBps(1000, 1000));
        assertEquals(19600, Odds.multiplierBps(5000)); // 1.96x
    }

    @Test
    void theChanceIsTheEloExpectedScoreRoundedOnce() {
        assertEquals(7597, Odds.redChanceBps(1200, 1000)); // 1 / (1 + 10^-0.5) = 0.75974
        assertEquals(2403, Odds.redChanceBps(1000, 1200));
        assertEquals(12899, Odds.multiplierBps(7597));
        assertEquals(40782, Odds.multiplierBps(10000 - 7597));
    }

    @Test
    void theChanceIsClampedToTenToNinetyPercent() {
        assertEquals(9000, Odds.redChanceBps(1500, 1000)); // 94.68% unclamped
        assertEquals(1000, Odds.redChanceBps(1000, 1500));
        assertEquals(10888, Odds.multiplierBps(9000)); // about 1.09x
        assertEquals(98000, Odds.multiplierBps(1000)); // 9.8x
    }

    @Test
    void thePayoutCapDropsTheStakeOptionsThatWouldPayTooMuch() {
        // 10,000 / 9.8 = 1020.4, so at 9.8x a stake of 1020 can win at most 9,996 and 1021 would pay 10,005
        assertEquals(List.of(10L, 100L, 500L, 1020L), Odds.stakes(List.of(10L, 100L, 500L, 1020L, 1021L, 2000L), 10_000, 98000));
        assertEquals(List.of(10L, 100L, 500L), Odds.stakes(List.of(10L, 100L, 500L), 10_000, 19600)); // 1.96x: nothing near the cap
        assertEquals(List.of(), Odds.stakes(List.of(2000L), 10_000, 98000));
    }
}
