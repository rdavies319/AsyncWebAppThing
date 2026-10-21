package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.RollKind;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The seeded dice roll from spec section 6. The expected values were worked out with openssl
 * (HMAC-SHA256 over the message) and shell arithmetic, not with this code.
 */
class DiceTests {
    private static final byte[] SEED = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

    @Test
    void knownAnswers() {
        // HMAC("1:1:red:hit:0") starts c6b5cf5d = 3333803869; 3333803869 mod 100 + 1
        assertEquals(70, Dice.roll(SEED, 1, 1, Corner.RED, RollKind.HIT, 100));
        // HMAC("1:1:blue:dmg:0") starts ed7caf03 = 3984371459; 3984371459 mod 19 + 1
        assertEquals(1, Dice.roll(SEED, 1, 1, Corner.BLUE, RollKind.DMG, 19));
        // HMAC("42:0:red:pair:0") starts dd7b26bd = 3715835581; 3715835581 mod 3 + 1
        assertEquals(2, Dice.roll(SEED, 42, 0, Corner.RED, RollKind.PAIR, 3));
    }

    @Test
    void aWordAtOrAboveTheLimitIsRejected() {
        // k = 1431655766 gives limit 2863311532. HMAC("42:7:blue:hit:0") starts fadd1658 = 4208793176,
        // which is rejected, then 33fc9abd = 872192701, which is taken: 872192701 mod k + 1
        assertEquals(872192702, Dice.roll(SEED, 42, 7, Corner.BLUE, RollKind.HIT, 1431655766));
    }

    @Test
    void rollsStayInRangeAndCoverIt() {
        Random random = new Random(1);
        for (int k : new int[] {1, 2, 3, 7, 100}) {
            boolean[] seen = new boolean[k + 1];
            for (int i = 0; i < 2000; i++) {
                byte[] seed = new byte[32];
                random.nextBytes(seed);
                int roll = Dice.roll(seed, i, i % 30 + 1, Corner.RED, RollKind.DMG, k);
                assertTrue(roll >= 1 && roll <= k, "roll " + roll + " outside 1.." + k);
                seen[roll] = true;
            }
            for (int v = 1; v <= k; v++) {
                assertTrue(seen[v], v + " never rolled from 1.." + k);
            }
        }
    }

    @Test
    void kMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> Dice.roll(SEED, 1, 1, Corner.RED, RollKind.HIT, 0));
    }
}
