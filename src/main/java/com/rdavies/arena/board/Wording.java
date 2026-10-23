package com.rdavies.arena.board;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Result;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.FightRecord;
import com.rdavies.arena.fight.records.RoundView;

/** How the game words fights for viewers, shared by the board and the fight pages. */
public final class Wording {
    private Wording() {}

    /** A rate in basis points as a percentage to one place, rounded down: 5234 is "52.3%". "–" for none. */
    public static String percent(Integer bps) {
        return bps == null ? "–" : bps / 100 + "." + bps % 100 / 10 + "%";
    }

    /** A value in hundredths, to two places: 1234 is "12.34". "–" for none. */
    public static String hundredths(Integer x100) {
        return x100 == null ? "–" : x100 / 100 + "." + String.format("%02d", x100 % 100);
    }

    /** "2. Brutus hits Vex for 14 · Brutus dodges": red's attack on blue, then blue's on red. */
    public static String logLine(RoundView round, String red, String blue) {
        String redAttack = round.redHit() ? red + " hits " + blue + " for " + round.redDamage() : blue + " dodges";
        String blueAttack = round.blueHit() ? blue + " hits " + red + " for " + round.blueDamage() : red + " dodges";
        return round.n() + ". " + redAttack + " · " + blueAttack;
    }

    /**
     * A round for the fight page: the log line, then the HP both were left on, never below 0.
     * "2. Brutus hits Vex for 14 · Brutus dodges (Brutus 106 HP, Vex 92 HP)"
     */
    public static String roundLine(RoundView round, String red, String blue) {
        return logLine(round, red, blue) + " (" + red + " " + Math.max(0, round.redHpAfter()) + " HP, "
                + blue + " " + Math.max(0, round.blueHpAfter()) + " HP)";
    }

    /** A W–L–D record: "3–1–0". */
    public static String record(int wins, int losses, int draws) {
        return wins + "–" + losses + "–" + draws;
    }

    /** One of a gladiator's results, from their side: "Won by KO", "Drew: double KO", "Lost on HP" and so on. */
    public static String recentResult(Result result, EndedBy endedBy) {
        if (endedBy == EndedBy.DOUBLE_KO) {
            return "Drew: double KO";
        }
        String outcome = switch (result) {
            case WON -> "Won";
            case LOST -> "Lost";
            case DREW -> "Drew";
        };
        return outcome + (endedBy == EndedBy.KO ? " by KO" : " on HP");
    }

    /** One line on a fight in any status: its result once settled, otherwise how far it has got. */
    public static String summary(FightRecord fight) {
        return switch (fight.status()) {
            case ANNOUNCED -> "Not fought yet";
            case LIVE -> "Fighting now: " + fight.roundsDone() + " rounds so far";
            case SETTLED -> result(fight.red().name(), fight.blue().name(), fight.winner(), fight.endedBy(),
                    fight.roundsDone());
        };
    }

    /** "Brutus beat Vex by KO", "Draw: level on HP after 30 rounds" and so on, for a settled fight. */
    public static String result(String red, String blue, Winner winner, EndedBy endedBy, int roundsDone) {
        String beat = winner == Winner.RED ? red + " beat " + blue : blue + " beat " + red;
        return switch (endedBy) {
            case DOUBLE_KO -> "Draw: " + red + " and " + blue + " knocked each other out";
            case KO -> beat + " by KO";
            case DECISION -> winner == Winner.DRAW
                    ? "Draw: level on HP after " + roundsDone + " rounds"
                    : beat + " on HP after " + roundsDone + " rounds";
        };
    }
}
