package com.rdavies.arena.fight;

import com.rdavies.arena.gladiator.Roster;
import com.rdavies.arena.gladiator.records.RosterEntry;
import com.rdavies.arena.gladiator.records.Stats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The simulated season that gives the dev roster its starting ratings. */
class EloSeasonTests {
    private static final int MAX_ROUNDS = 30;
    private static final Stats STRONG = new Stats(140, 15, 22, 6, 30);
    private static final Stats WEAK = new Stats(80, 4, 8, 0, 5);
    private static final Stats AVERAGE = new Stats(110, 8, 16, 2, 15);

    private static List<RosterEntry> devRoster;

    @BeforeAll
    static void loadDevRoster() throws IOException {
        try (InputStream in = EloSeasonTests.class.getResourceAsStream("/roster-dev.json")) {
            devRoster = Roster.read(in);
        }
    }

    @Test
    void everyoneStartsAtAThousandAndTheSeasonIsZeroSum() {
        List<Integer> ratings = EloSeason.replay(stats(devRoster), MAX_ROUNDS);

        assertEquals(devRoster.size(), ratings.size());
        assertEquals(1000 * devRoster.size(), ratings.stream().mapToInt(Integer::intValue).sum());
        assertNotEquals(Collections.nCopies(devRoster.size(), 1000), ratings);
    }

    @Test
    void theSameRosterAlwaysGetsTheSameRatings() {
        assertEquals(EloSeason.replay(stats(devRoster), MAX_ROUNDS), EloSeason.replay(stats(devRoster), MAX_ROUNDS));
    }

    @Test
    void aMuchStrongerGladiatorEndsFarAhead() {
        List<Integer> ratings = EloSeason.replay(List.of(STRONG, WEAK), MAX_ROUNDS);

        assertTrue(ratings.get(0) - ratings.get(1) > 400, ratings.toString());
    }

    @Test
    void identicalGladiatorsStayClose() {
        List<Integer> ratings = EloSeason.replay(List.of(AVERAGE, AVERAGE, AVERAGE, AVERAGE), MAX_ROUNDS);

        for (int rating : ratings) {
            assertTrue(Math.abs(rating - 1000) < 150, ratings.toString());
        }
    }

    @Test
    void theDevRostersBestAndWorstRecordsGetTheTopAndBottomRatings() {
        BalanceSimulation.Report report = BalanceSimulation.run(devRoster, 500, MAX_ROUNDS);

        List<Integer> ratings = EloSeason.replay(stats(devRoster), MAX_ROUNDS);

        assertEquals(report.strongest(), devRoster.get(ratings.indexOf(Collections.max(ratings))).slug(), report.toString());
        assertEquals(report.weakest(), devRoster.get(ratings.indexOf(Collections.min(ratings))).slug(), report.toString());
    }

    private static List<Stats> stats(List<RosterEntry> roster) {
        return roster.stream().map(RosterEntry::stats).toList();
    }
}
