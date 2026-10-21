package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.Winner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Elo update when a fight settles, from spec section 5 (Elo update): K = 32, zero-sum. */
class EloTests {

    @Test
    void betweenEqualsAWinIsWorthHalfOfK() {
        assertEquals(16, Elo.redDelta(1000, 1000, Winner.RED));
        assertEquals(-16, Elo.redDelta(1000, 1000, Winner.BLUE));
        assertEquals(0, Elo.redDelta(1000, 1000, Winner.DRAW));
    }

    @Test
    void theFavouriteGainsLittleForAWinAndLosesMoreForALoss() {
        // red's expected score at 1200 v 1000 is 0.75974
        assertEquals(8, Elo.redDelta(1200, 1000, Winner.RED));    // 32 × 0.24026 = 7.69
        assertEquals(-24, Elo.redDelta(1200, 1000, Winner.BLUE)); // 32 × -0.75974 = -24.31
        assertEquals(-8, Elo.redDelta(1200, 1000, Winner.DRAW));  // 32 × -0.25974 = -8.31
    }

    @Test
    void theExpectedScoreIsNotClamped() {
        // red's expected score at 1000 v 1500 is 0.05324, below the odds' 10% floor
        assertEquals(30, Elo.redDelta(1000, 1500, Winner.RED)); // 32 × 0.94676 = 30.30; clamped would give 29
    }
}
