package com.rdavies.api.round;

import com.rdavies.api.prediction.PredictionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OddsTests {

    @Test
    void evenOddsPayJustUnderDouble() {
        // R = 50: 50 rolls are higher, so multiplier = 0.98 / 0.50 = 1.96
        assertEquals(196, Odds.payout(50, PredictionType.HIGHER, 100));
    }

    @Test
    void longShotPaysMore() {
        // R = 99: only 100 wins, so multiplier = 0.98 / 0.01 = 98
        assertEquals(980, Odds.payout(99, PredictionType.HIGHER, 10));
    }

    @Test
    void payoutRoundsDown() {
        // R = 4, LOWER: 3 winning rolls, 10 * 98 / 3 = 326.67
        assertEquals(326, Odds.payout(4, PredictionType.LOWER, 10));
    }

    @Test
    void impossibleDirectionsAreNotAllowed() {
        assertFalse(Odds.isAllowed(100, PredictionType.HIGHER));
        assertFalse(Odds.isAllowed(1, PredictionType.LOWER));
        assertTrue(Odds.isAllowed(1, PredictionType.HIGHER));
        assertThrows(IllegalArgumentException.class, () -> Odds.payout(100, PredictionType.HIGHER, 10));
    }
}
