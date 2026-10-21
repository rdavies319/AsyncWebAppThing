package com.rdavies.api.round;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.OptionalInt;

/**
 * Commit-reveal for rolls (spec section 4). Anyone can check a rolled round with its seed and id:
 * <ul>
 *   <li>seed = 32 random bytes, published as 64 hex characters once the round is rolled;</li>
 *   <li>commit = hex(SHA-256(seed bytes)), published before anyone bets on the roll;</li>
 *   <li>roll: for c = 0, 1, 2, ..., h = HMAC-SHA256(key = seed bytes, message = "round_id:c");
 *       the first 4-byte big-endian word u of h with u &lt; 4294967200 gives roll = u mod 100 + 1.
 *       Rejecting the top 96 values of 2^32 removes the modulo bias.</li>
 * </ul>
 */
public final class ProvablyFair {
    private static final HexFormat HEX = HexFormat.of();
    /** Largest multiple of 100 not above 2^32: words at or above it are rejected. */
    static final long ACCEPT_BELOW = (1L << 32) / 100 * 100;

    private ProvablyFair() {}

    public static String newSeed(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    public static String commit(String seed) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(HEX.parseHex(seed)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    public static int roll(String seed, long roundId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HEX.parseHex(seed), "HmacSHA256"));
            for (int counter = 0; ; counter++) {
                byte[] block = mac.doFinal((roundId + ":" + counter).getBytes(StandardCharsets.US_ASCII));
                OptionalInt roll = firstAcceptedRoll(block);
                if (roll.isPresent()) {
                    return roll.getAsInt();
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by every Java platform", e);
        }
    }

    /** The roll from the first acceptable word of one HMAC block, or empty if all eight are rejected. */
    static OptionalInt firstAcceptedRoll(byte[] block) {
        ByteBuffer words = ByteBuffer.wrap(block);
        while (words.remaining() >= Integer.BYTES) {
            long word = Integer.toUnsignedLong(words.getInt());
            if (word < ACCEPT_BELOW) {
                return OptionalInt.of((int) (word % 100) + 1);
            }
        }
        return OptionalInt.empty();
    }
}
