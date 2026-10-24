package com.rdavies.arena.fight;

import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.gladiator.records.Stats;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simulated season, for the dev roster's starting ratings: every gladiator starts at 1000, and
 * every pairing fights {@link #ROUND_ROBINS} times, in order, through the real Elo update. Built from
 * the hidden stats, so it is for the dev profile only, whose roster is checked in (spec section 5:
 * real odds come from public data only). Deterministic: the same roster always gets the same ratings.
 */
final class EloSeason {
    static final int ROUND_ROBINS = 50;

    private EloSeason() {}

    /** Each gladiator's rating after the season, in roster order. */
    static List<Integer> replay(List<Stats> roster, int maxRounds) {
        int[] ratings = new int[roster.size()];
        Arrays.fill(ratings, 1000);
        int fightNo = 0;
        for (int round = 0; round < ROUND_ROBINS; round++) {
            for (int a = 0; a < roster.size(); a++) {
                for (int b = a + 1; b < roster.size(); b++) {
                    fightNo++;
                    // Corners swap every round, so neither side favours one
                    int red = round % 2 == 0 ? a : b;
                    int blue = red == a ? b : a;
                    Outcome outcome = Combat.simulate(seed(fightNo), fightNo, roster.get(red), roster.get(blue),
                            maxRounds).outcome();
                    int delta = Elo.redDelta(ratings[red], ratings[blue], outcome.winner());
                    ratings[red] += delta;
                    ratings[blue] -= delta;
                }
            }
        }
        List<Integer> result = new ArrayList<>(ratings.length);
        for (int rating : ratings) {
            result.add(rating);
        }
        return List.copyOf(result);
    }

    /** A fixed seed per fight of the season. */
    private static byte[] seed(int fightNo) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    ByteBuffer.allocate(16).putLong(0x534541534F4EL).putInt(fightNo).array()); // "SEASON"
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
