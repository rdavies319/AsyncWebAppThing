package com.rdavies.arena.fight;

import com.rdavies.arena.fight.records.Pair;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who fights next (spec section 4, Pairing). Tie-break rolls were worked out with openssl, for
 * seed 00..1f and fight 42: roll(red, pair, k=3) = 2, roll(red, pair, k=2) = 2 and roll(blue, pair, k=2) = 2.
 */
class PairingTests {
    private static final byte[] SEED = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    private static final long FIGHT = 42;
    private static final Instant T1 = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant T2 = T1.plusSeconds(3600);
    private static final Instant T3 = T2.plusSeconds(3600);
    private static final Instant T4 = T3.plusSeconds(3600);
    /** Fights in the simulated season: every one of 8 gladiators' 28 matchups should have come round by then. */
    private static final int SEASON = 56;
    /** The most fights a gladiator sits out between two of their own, with 8 gladiators (4 under the old rule). */
    private static final int LONGEST_WAIT = 6;

    @Test
    void theCurrentFightersSitItOut() {
        Map<Long, Instant> lastFought = Map.of(1L, T1, 2L, T2, 3L, T3, 4L, T4);

        assertEquals(new Pair(3, 4), Pairing.pick(List.of(1L, 2L, 3L, 4L), lastFought, Set.of(1L, 2L), Map.of(), SEED, FIGHT));
    }

    @Test
    void whoeverFoughtLongestAgoGoesFirst() {
        Map<Long, Instant> lastFought = Map.of(1L, T3, 2L, T1, 3L, T2, 4L, T4);

        assertEquals(new Pair(2, 3), Pairing.pick(List.of(1L, 2L, 3L, 4L), lastFought, Set.of(), Map.of(), SEED, FIGHT));
    }

    @Test
    void aGladiatorWhoHasNeverFoughtGoesBeforeEveryone() {
        Map<Long, Instant> lastFought = Map.of(1L, T1, 3L, T2); // 2 and 4 never fought

        // red: the 2nd of [2, 4]
        assertEquals(new Pair(4, 2), Pairing.pick(List.of(1L, 2L, 3L, 4L), lastFought, Set.of(), Map.of(), SEED, FIGHT));
    }

    @Test
    void theSeedBreaksATie() {
        Map<Long, Instant> lastFought = Map.of(8L, T1); // 5, 6 and 7 never fought

        // red: the 2nd of [5, 6, 7]; blue: the 2nd of what's left, [5, 7]
        assertEquals(new Pair(6, 7), Pairing.pick(List.of(5L, 6L, 7L, 8L), lastFought, Set.of(), Map.of(), SEED, FIGHT));
    }

    @Test
    void theSeedBreaksATieForTheSecondPickOnly() {
        Map<Long, Instant> lastFought = Map.of(2L, T1, 3L, T1, 4L, T2); // 1 never fought; 2 and 3 tie

        // blue: the 2nd of [2, 3]
        assertEquals(new Pair(1, 3), Pairing.pick(List.of(1L, 2L, 3L, 4L), lastFought, Set.of(), Map.of(), SEED, FIGHT));
    }

    @Test
    void blueIsNoneOfRedsLastThreeOpponents() {
        Map<Long, Instant> lastFought = Map.of(1L, T1, 2L, T2, 3L, T3, 4L, T4, 5L, T4.plusSeconds(3600));

        assertEquals(new Pair(1, 5), Pairing.pick(List.of(1L, 2L, 3L, 4L, 5L), lastFought, Set.of(),
                Map.of(1L, List.of(4L, 2L, 3L)), SEED, FIGHT));
    }

    @Test
    void anOpponentFourFightsBackIsFairGame() {
        Map<Long, Instant> lastFought = Map.of(1L, T1, 2L, T2, 3L, T3, 4L, T4, 5L, T4.plusSeconds(3600));

        assertEquals(new Pair(1, 2), Pairing.pick(List.of(1L, 2L, 3L, 4L, 5L), lastFought, Set.of(),
                Map.of(1L, List.of(3L, 4L, 5L, 2L)), SEED, FIGHT));
    }

    @Test
    void whenEveryoneFreeIsARecentOpponentRestAloneDecides() {
        Map<Long, Instant> lastFought = Map.of(1L, T1, 2L, T2, 3L, T3);

        assertEquals(new Pair(1, 2), Pairing.pick(List.of(1L, 2L, 3L), lastFought, Set.of(),
                Map.of(1L, List.of(3L, 2L)), SEED, FIGHT));
    }

    /** The bug this rule fixed: pairs who fought together rested together, so they were paired again forever. */
    @Test
    void everyMatchupComesRoundAndNobodyWaitsLong() {
        List<Long> roster = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        Map<Long, Instant> lastFought = new HashMap<>();
        Map<Long, List<Long>> opponents = new HashMap<>();   // most recent first
        Map<Long, Integer> lastFight = new HashMap<>();
        Set<Set<Long>> met = new HashSet<>();
        Set<Long> fighting = Set.of();
        for (int n = 1; n <= SEASON; n++) {
            byte[] seed = new byte[32];
            Arrays.fill(seed, (byte) n);
            Pair pair = Pairing.pick(roster, lastFought, fighting, opponents, seed, n);
            met.add(Set.of(pair.redId(), pair.blueId()));
            for (long[] meeting : new long[][] {{pair.redId(), pair.blueId()}, {pair.blueId(), pair.redId()}}) {
                Integer previous = lastFight.put(meeting[0], n);
                assertTrue(previous == null || n - previous <= LONGEST_WAIT, meeting[0] + " waited " + (previous == null ? 0 : n - previous) + " fights");
                lastFought.put(meeting[0], T1.plusSeconds(3600L * n));
                opponents.computeIfAbsent(meeting[0], id -> new ArrayList<>()).addFirst(meeting[1]);
            }
            fighting = Set.of(pair.redId(), pair.blueId());
        }
        assertEquals(28, met.size(), "distinct matchups in " + SEASON + " fights");
    }

    @Test
    void theSameSeedAlwaysPicksTheSamePair() {
        List<Long> roster = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L); // nobody has fought: one big tie

        Pair first = Pairing.pick(roster, Map.of(), Set.of(), Map.of(), SEED, FIGHT);

        assertEquals(first, Pairing.pick(roster, Map.of(), Set.of(), Map.of(), SEED, FIGHT));
    }
}
