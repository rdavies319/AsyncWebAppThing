package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.SimulatedFight;
import com.rdavies.arena.gladiator.records.RosterEntry;
import com.rdavies.arena.gladiator.records.Stats;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays many seeded fights for every pairing in a roster and reports the balance targets from
 * spec section 3. Deterministic: the same roster and fight count always give the same report.
 */
final class BalanceSimulation {
    private BalanceSimulation() {}

    /**
     * @param strongestBeatsWeakest how often the gladiator with the best record beats the one with
     *                              the worst, head to head (a draw is not a win)
     * @param medianLength          the median fight length in rounds, over every fight
     * @param meanLength            the mean fight length in rounds
     * @param capRate               the share of fights that reached the round cap
     * @param scores                each gladiator's score against the field (a draw counts half), by slug
     */
    record Report(String strongest, String weakest, double strongestBeatsWeakest,
                  int medianLength, double meanLength, double capRate, Map<String, Double> scores) {
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder("%s beats %s %.1f%%; median %d rounds (mean %.1f); %.1f%% reach the cap%n"
                    .formatted(strongest, weakest, strongestBeatsWeakest * 100, medianLength, meanLength, capRate * 100));
            scores.forEach((slug, score) -> s.append("  %-12s %.1f%%%n".formatted(slug, score * 100)));
            return s.toString();
        }
    }

    private record Pairing(int a, int b, int aWins, int bWins, int[] lengths, int capped) {}

    static Report run(List<RosterEntry> roster, int fightsPerPairing, int maxRounds) {
        List<int[]> pairs = new ArrayList<>();
        for (int a = 0; a < roster.size(); a++) {
            for (int b = a + 1; b < roster.size(); b++) {
                pairs.add(new int[] {a, b});
            }
        }
        List<Pairing> results = pairs.parallelStream()
                .map(p -> play(roster, p[0], p[1], fightsPerPairing, maxRounds)).toList();

        double[] points = new double[roster.size()];
        int[] fights = new int[roster.size()];
        int capped = 0;
        int[] lengths = new int[results.size() * fightsPerPairing];
        int at = 0;
        for (Pairing r : results) {
            double draws = fightsPerPairing - r.aWins() - r.bWins();
            points[r.a()] += r.aWins() + draws / 2;
            points[r.b()] += r.bWins() + draws / 2;
            fights[r.a()] += fightsPerPairing;
            fights[r.b()] += fightsPerPairing;
            capped += r.capped();
            System.arraycopy(r.lengths(), 0, lengths, at, fightsPerPairing);
            at += fightsPerPairing;
        }
        Arrays.sort(lengths);

        Map<String, Double> scores = new LinkedHashMap<>();
        int strongest = 0;
        int weakest = 0;
        for (int i = 0; i < roster.size(); i++) {
            scores.put(roster.get(i).slug(), points[i] / fights[i]);
            if (points[i] > points[strongest]) strongest = i;
            if (points[i] < points[weakest]) weakest = i;
        }
        Pairing head = head(results, strongest, weakest);
        int strongestWins = head.a() == strongest ? head.aWins() : head.bWins();
        return new Report(roster.get(strongest).slug(), roster.get(weakest).slug(),
                (double) strongestWins / fightsPerPairing, lengths[lengths.length / 2],
                Arrays.stream(lengths).average().orElse(0),
                (double) capped / lengths.length, scores);
    }

    /** Plays {@code a} against {@code b}, swapping corners every fight so neither side favours one. */
    private static Pairing play(List<RosterEntry> roster, int a, int b, int fights, int maxRounds) {
        Stats sa = roster.get(a).stats();
        Stats sb = roster.get(b).stats();
        int aWins = 0;
        int bWins = 0;
        int capped = 0;
        int[] lengths = new int[fights];
        for (int i = 0; i < fights; i++) {
            boolean aIsRed = i % 2 == 0;
            SimulatedFight fight = Combat.simulate(seed(a, b, i), i + 1, aIsRed ? sa : sb, aIsRed ? sb : sa, maxRounds);
            Winner aCorner = aIsRed ? Winner.RED : Winner.BLUE;
            Winner winner = fight.outcome().winner();
            if (winner == aCorner) aWins++;
            else if (winner != Winner.DRAW) bWins++;
            if (fight.outcome().endedBy() == EndedBy.DECISION) capped++;
            lengths[i] = fight.rounds().size();
        }
        return new Pairing(a, b, aWins, bWins, lengths, capped);
    }

    private static Pairing head(List<Pairing> results, int x, int y) {
        return results.stream().filter(r -> (r.a() == x && r.b() == y) || (r.a() == y && r.b() == x))
                .findFirst().orElseThrow();
    }

    /** A fixed seed per fight, so a report never changes between runs. */
    private static byte[] seed(int a, int b, int i) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    ByteBuffer.allocate(12).putInt(a).putInt(b).putInt(i).array());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
