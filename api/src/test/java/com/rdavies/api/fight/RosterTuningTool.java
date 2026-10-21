package com.rdavies.api.fight;

import com.rdavies.api.gladiator.Roster;
import com.rdavies.api.gladiator.records.RosterEntry;
import com.rdavies.api.gladiator.records.Stats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The offline tool that makes a roster (spec section 3, Tuning). It keeps the identities of an
 * input roster and hill-climbs the stats until RosterBalanceTests' targets hold, then writes the
 * roster to a file outside the repo. Skipped unless {@code roster.out} is set:
 *
 * <pre>./mvnw test -Dtest=RosterTuningTool -Droster.out=/secrets/roster.json [-Droster.in=identities.json] [-Droster.seed=42]</pre>
 *
 * The production roster is this tool's output with a seed nobody else knows; keep it out of git.
 */
@EnabledIfSystemProperty(named = "roster.out", matches = ".+")
class RosterTuningTool {
    /** Fights per pairing while searching: rough but fast. A candidate is then checked with the test's count. */
    private static final int SEARCH_FIGHTS = 200;
    private static final int ITERATIONS = Integer.getInteger("roster.iterations", 3000);

    @Test
    void searchForABalancedRoster() throws IOException {
        Path out = Path.of(System.getProperty("roster.out")).toAbsolutePath().normalize();
        Path repo = Path.of("").toAbsolutePath().getParent(); // tests run from api/
        if (out.startsWith(repo)) {
            fail("roster.out must be outside the repo (" + repo + "): the real roster never goes into git");
        }
        long seed = Long.getLong("roster.seed", System.nanoTime());
        Random random = new Random(seed);
        List<RosterEntry> current = randomStats(identities(), random);
        double score = score(BalanceSimulation.run(current, SEARCH_FIGHTS, RosterBalanceTests.MAX_EXCHANGES));

        for (int i = 0; i < ITERATIONS; i++) {
            if (score == 0) {
                BalanceSimulation.Report full = BalanceSimulation.run(current, RosterBalanceTests.FIGHTS_PER_PAIRING,
                        RosterBalanceTests.MAX_EXCHANGES);
                if (score(full) == 0) {
                    write(current, out);
                    System.out.printf("Roster written to %s after %d steps (roster.seed=%d)%n%s", out, i, seed, full);
                    return;
                }
                score = score(full); // the rough count was lucky: keep climbing from the accurate score
            }
            List<RosterEntry> candidate = mutate(current, random);
            double candidateScore = score(BalanceSimulation.run(candidate, SEARCH_FIGHTS, RosterBalanceTests.MAX_EXCHANGES));
            if (candidateScore <= score) {
                current = candidate;
                score = candidateScore;
            }
        }
        fail("no balanced roster after " + ITERATIONS + " steps; try another roster.seed. Closest (score "
                + score + "):\n" + BalanceSimulation.run(current, RosterBalanceTests.FIGHTS_PER_PAIRING, RosterBalanceTests.MAX_EXCHANGES));
    }

    /**
     * 0 when every target holds with a margin, so the checked roster passes RosterBalanceTests
     * comfortably; otherwise how far it is from them.
     */
    private static double score(BalanceSimulation.Report report) {
        double winRate = Math.max(0, Math.abs(report.strongestBeatsWeakest() - 0.70) - 0.025) * 20;
        // The median moves in whole exchanges, so a one-stat nudge rarely shifts it; the mean gives the climb a slope
        double length = Math.max(0, 10 - report.medianLength()) + Math.max(0, report.medianLength() - 13)
                + Math.max(0, 10 - report.meanLength()) + Math.max(0, report.meanLength() - 13);
        double cap = Math.max(0, report.capRate() - 0.025) * 40;
        // Only the two extremes count towards the win-rate target, so nudging them just hands the
        // extreme to someone else. Pulling every gladiator towards the middle of the field closes
        // the gap as a whole; a 70% head-to-head needs scores of roughly 35-65% against the field.
        double spread = report.scores().values().stream()
                .mapToDouble(s -> Math.max(0, Math.abs(s - 0.5) - 0.17) * 10).sum();
        return winRate + length + cap + spread;
    }

    private static List<RosterEntry> randomStats(List<RosterEntry> identities, Random random) {
        List<RosterEntry> roster = new ArrayList<>();
        for (RosterEntry entry : identities) {
            int attackMin = between(random, 4, 10);
            roster.add(with(entry, new Stats(between(random, 85, 135), attackMin,
                    between(random, attackMin + 4, Math.min(22, attackMin + 12)),
                    between(random, 0, 5), between(random, 5, 28))));
        }
        return roster;
    }

    /** Nudges one stat of one gladiator, staying inside the spec's typical ranges. */
    private static List<RosterEntry> mutate(List<RosterEntry> roster, Random random) {
        List<RosterEntry> next = new ArrayList<>(roster);
        int i = random.nextInt(roster.size());
        Stats s = roster.get(i).stats();
        int step = random.nextBoolean() ? 1 : -1;
        Stats changed = switch (random.nextInt(5)) {
            case 0 -> new Stats(clamp(s.hp() + 5 * step, 80, 140), s.attackMin(), s.attackMax(), s.defence(), s.agility());
            case 1 -> new Stats(s.hp(), clamp(s.attackMin() + step, 4, s.attackMax()), s.attackMax(), s.defence(), s.agility());
            case 2 -> new Stats(s.hp(), s.attackMin(), clamp(s.attackMax() + step, s.attackMin(), 22), s.defence(), s.agility());
            case 3 -> new Stats(s.hp(), s.attackMin(), s.attackMax(), clamp(s.defence() + step, 0, 6), s.agility());
            default -> new Stats(s.hp(), s.attackMin(), s.attackMax(), s.defence(), clamp(s.agility() + 2 * step, 5, 30));
        };
        next.set(i, with(roster.get(i), changed));
        return next;
    }

    private static List<RosterEntry> identities() throws IOException {
        String in = System.getProperty("roster.in");
        try (InputStream stream = in != null ? Files.newInputStream(Path.of(in))
                : RosterTuningTool.class.getResourceAsStream("/roster-dev.json")) {
            return Roster.read(stream);
        }
    }

    private static void write(List<RosterEntry> roster, Path out) throws IOException {
        JsonMapper json = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        Files.writeString(out, json.writeValueAsString(roster));
    }

    private static RosterEntry with(RosterEntry entry, Stats stats) {
        return new RosterEntry(entry.slug(), entry.name(), entry.epithet(), entry.bio(), stats);
    }

    private static int between(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
