package com.rdavies.api.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Commit-reveal for rolls. Anyone can check a settled round with nothing but SHA-256:
 * <ul>
 *   <li>commit = hex(SHA-256(seed)), where seed is the 64-char hex string as UTF-8;</li>
 *   <li>roll = 1 + (first 8 bytes of SHA-256(seed + ":roll"), unsigned) mod 100.</li>
 * </ul>
 * The modulo bias over 2^64 is around 10^-17, far below anything observable.
 */
public final class ProvablyFair {
    private static final HexFormat HEX = HexFormat.of();

    private ProvablyFair() {}

    public static String newSeed(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    public static String commit(String seed) {
        return HEX.formatHex(sha256(seed));
    }

    public static int roll(String seed) {
        long value = ByteBuffer.wrap(sha256(seed + ":roll")).getLong();
        return (int) Long.remainderUnsigned(value, 100) + 1;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
