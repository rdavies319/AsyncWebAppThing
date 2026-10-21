package com.rdavies.arena.fight;

import com.rdavies.arena.gladiator.Roster;
import com.rdavies.arena.gladiator.records.RosterEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat feel from spec section 3, checked on the dev roster, so a change to the combat rules
 * can't quietly break the balance. RosterTuningTool searches for rosters that pass this.
 */
class RosterBalanceTests {
    static final int FIGHTS_PER_PAIRING = 2000;
    static final int MAX_ROUNDS = 30;

    private static BalanceSimulation.Report report;

    @BeforeAll
    static void simulate() throws IOException {
        try (InputStream in = RosterBalanceTests.class.getResourceAsStream("/roster-dev.json")) {
            List<RosterEntry> roster = Roster.read(in);
            report = BalanceSimulation.run(roster, FIGHTS_PER_PAIRING, MAX_ROUNDS);
        }
    }

    @Test
    void theStrongestBeatsTheWeakestAboutSevenTimesInTen() {
        assertTrue(meetsWinRateTarget(report), report.toString());
    }

    @Test
    void theMedianFightLastsEightToFifteenRounds() {
        assertTrue(meetsLengthTarget(report), report.toString());
    }

    @Test
    void fewFightsReachTheCap() {
        assertTrue(meetsCapTarget(report), report.toString());
    }

    static boolean meetsWinRateTarget(BalanceSimulation.Report report) {
        return report.strongestBeatsWeakest() >= 0.65 && report.strongestBeatsWeakest() <= 0.75;
    }

    static boolean meetsLengthTarget(BalanceSimulation.Report report) {
        return report.medianLength() >= 8 && report.medianLength() <= 15;
    }

    static boolean meetsCapTarget(BalanceSimulation.Report report) {
        return report.capRate() < 0.05;
    }
}
