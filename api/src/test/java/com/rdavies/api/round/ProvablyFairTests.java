package com.rdavies.api.round;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class ProvablyFairTests {
    /** Bytes 0x00..0x1f. The expected values below were computed independently with openssl. */
    private static final String SEED = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Test
    void commitIsSha256OfTheSeedBytes() {
        // printf '%s' $SEED | xxd -r -p | openssl dgst -sha256
        assertEquals("630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd",
                ProvablyFair.commit(SEED));
    }

    @Test
    void rollMatchesKnownAnswers() {
        // printf '7:0' | openssl dgst -sha256 -mac HMAC -macopt hexkey:$SEED  -> a9441663...
        // 0xa9441663 = 2839811683, accepted; 2839811683 mod 100 + 1 = 84
        assertEquals(84, ProvablyFair.roll(SEED, 7));
        // '42:0' -> c1bbf5a1... = 3250320801; mod 100 + 1 = 2
        assertEquals(2, ProvablyFair.roll(SEED, 42));
    }

    @Test
    void rollsAreAlwaysOneToHundred() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 2_000; i++) {
            int roll = ProvablyFair.roll(ProvablyFair.newSeed(random), i);
            assertTrue(roll >= 1 && roll <= 100, "roll " + roll);
        }
    }

    @Test
    void wordsInTheBiasedTailAreRejected() {
        assertEquals(4_294_967_200L, ProvablyFair.ACCEPT_BELOW);
        byte[] block = new byte[32];
        // First word 0xFFFFFFFF (rejected), second 0xFFFFFFA0 = 4294967200 (rejected: the boundary),
        // third 0xFFFFFF9F = 4294967199 (accepted): 4294967199 mod 100 + 1 = 100
        fillWord(block, 0, 0xFFFFFFFF);
        fillWord(block, 1, 0xFFFFFFA0);
        fillWord(block, 2, 0xFFFFFF9F);
        assertEquals(OptionalInt.of(100), ProvablyFair.firstAcceptedRoll(block));
    }

    @Test
    void aFullyRejectedBlockYieldsNothing() {
        byte[] block = new byte[32];
        for (int i = 0; i < 8; i++) {
            fillWord(block, i, 0xFFFFFFFF);
        }
        assertEquals(OptionalInt.empty(), ProvablyFair.firstAcceptedRoll(block));
    }

    private static void fillWord(byte[] block, int index, int word) {
        for (int b = 0; b < 4; b++) {
            block[index * 4 + b] = (byte) (word >>> (24 - 8 * b));
        }
    }
}
