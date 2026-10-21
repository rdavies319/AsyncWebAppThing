package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.RollKind;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * The fight's only source of chance (spec section 6): every roll is an HMAC-SHA256 of its position
 * in the fight, keyed by the fight's seed, so the seed replays a fight exactly.
 */
final class Dice {
    private static final long TWO_POW_32 = 1L << 32;

    private Dice() {}

    /**
     * A uniform integer in 1..k. Rejection sampling discards 32-bit words at or above the largest
     * multiple of k, so there is no modulo bias.
     */
    static int roll(byte[] seed, long fightId, int n, Corner corner, RollKind kind, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1, was " + k);
        }
        long limit = TWO_POW_32 / k * k;
        Mac mac = hmac(seed);
        for (int c = 0; ; c++) {
            String message = fightId + ":" + n + ":" + corner.label() + ":" + kind.label() + ":" + c;
            ByteBuffer words = ByteBuffer.wrap(mac.doFinal(message.getBytes(StandardCharsets.US_ASCII)));
            while (words.hasRemaining()) {
                long u = Integer.toUnsignedLong(words.getInt());
                if (u < limit) {
                    return (int) (u % k) + 1;
                }
            }
        }
    }

    private static Mac hmac(byte[] seed) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(seed, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
