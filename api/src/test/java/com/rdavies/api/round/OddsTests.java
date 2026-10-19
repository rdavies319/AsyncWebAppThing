package com.rdavies.api.round;

import com.rdavies.api.prediction.PredictionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OddsTests {

    @Test
    void evenOddsPayJustUnderDouble() {
        // R = 50: 50 rolls are higher, so multiplier = 0.98 / 0.50 = 1.96; 49 are lower: 0.98 / 0.49 = 2.00
        assertEquals(19_600, Odds.multiplierBps(50, PredictionType.HIGHER));
        assertEquals(20_000, Odds.multiplierBps(50, PredictionType.LOWER));
        assertEquals(196, Odds.payout(100, 19_600));
    }

    @Test
    void longShotPaysMore() {
        // R = 99: only 100 wins, so multiplier = 0.98 / 0.01 = 98
        assertEquals(980_000, Odds.multiplierBps(99, PredictionType.HIGHER));
        assertEquals(980, Odds.payout(10, 980_000));
    }

    @Test
    void specExampleAfterNinety() {
        assertEquals(98_000, Odds.multiplierBps(90, PredictionType.HIGHER));
        assertEquals(11_011, Odds.multiplierBps(90, PredictionType.LOWER));
    }

    @Test
    void multiplierAndPayoutRoundDown() {
        // R = 4, LOWER: 3 winning rolls, 980000 / 3 = 326666.67 bps; 10 * 32.6666 = 326.67 pts
        assertEquals(326_666, Odds.multiplierBps(4, PredictionType.LOWER));
        assertEquals(326, Odds.payout(10, 326_666));
    }

    @Test
    void payoutCapLowersTheMaxStakeOnLongShots() {
        // R = 99 HIGHER pays 98x: floor(10000 * 10000 / 980000) = 102
        assertEquals(102, Odds.maxStake(980_000, 500, 10_000));
        // R = 50 HIGHER pays 1.96x: 500 * 1.96 = 980 is well under the cap
        assertEquals(500, Odds.maxStake(19_600, 500, 10_000));
        // The capped stake really stays within the cap, and one more point would not
        assertTrue(Odds.payout(102, 980_000) <= 10_000);
        assertTrue(Odds.payout(103, 980_000) > 10_000);
    }

    @Test
    void impossibleDirectionsAreNotAllowed() {
        assertFalse(Odds.isAllowed(100, PredictionType.HIGHER));
        assertFalse(Odds.isAllowed(1, PredictionType.LOWER));
        assertTrue(Odds.isAllowed(1, PredictionType.HIGHER));
        assertThrows(IllegalArgumentException.class, () -> Odds.multiplierBps(100, PredictionType.HIGHER));
    }
}
