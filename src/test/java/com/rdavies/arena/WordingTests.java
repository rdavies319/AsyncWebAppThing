package com.rdavies.arena;

import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The viewer's text, without Spring: the board and page tests check which line shows where. */
class WordingTests {

    @Test
    void oddsRoundDownSoTheyNeverPromiseMoreThanAWinPays() {
        assertEquals("1.96x", Wording.odds(19_600));
        assertEquals("1.96x", Wording.odds(19_699));
        assertEquals("2.00x", Wording.odds(20_000));
        assertEquals("1.05x", Wording.odds(10_500));
    }

    @Test
    void eachWayAFightCanEndReadsForBothSides() {
        assertEquals("Won by KO", Wording.recentResult(Result.WON, EndedBy.KO));
        assertEquals("Lost by KO", Wording.recentResult(Result.LOST, EndedBy.KO));
        assertEquals("Drew: double KO", Wording.recentResult(Result.DREW, EndedBy.DOUBLE_KO));
        assertEquals("Won on HP", Wording.recentResult(Result.WON, EndedBy.DECISION));
        assertEquals("Lost on HP", Wording.recentResult(Result.LOST, EndedBy.DECISION));
        assertEquals("Drew on HP", Wording.recentResult(Result.DREW, EndedBy.DECISION));
        assertEquals("3–1–0", Wording.record(3, 1, 0));
    }

    @Test
    void aSettledBetReadsAsWonLostOrRefunded() {
        assertEquals("You won 196 on Vex", Wording.settledBet(BetStatus.WON, 100, 196L, "Vex"));
        assertEquals("You lost 100 on Vex", Wording.settledBet(BetStatus.LOST, 100, 0L, "Vex"));
        assertEquals("Draw: your 100 on Vex is refunded", Wording.settledBet(BetStatus.PUSH, 100, 100L, "Vex"));
        assertThrows(IllegalStateException.class, () -> Wording.settledBet(BetStatus.PENDING, 100, null, "Vex"));
    }

    @Test
    void aPastBetNamesTheMatchupTheOddsAndTheOutcome() {
        assertEquals("Brutus v Vex: 100 on Brutus at 1.96x · won 196",
                Wording.pastBet("Brutus", "Vex", "Brutus", 100, 19_600, BetStatus.WON, 196L));
        assertEquals("Brutus v Vex: 100 on Vex at 1.96x · pending",
                Wording.pastBet("Brutus", "Vex", "Vex", 100, 19_600, BetStatus.PENDING, null));
        assertEquals("Your bet: 100 on Vex at 1.96x", Wording.pendingBet(100, "Vex", 19_600));
    }
}
