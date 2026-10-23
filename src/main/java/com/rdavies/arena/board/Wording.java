package com.rdavies.arena.board;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.FightRecord;
import com.rdavies.arena.fight.records.RoundView;

/** How the game words fights for viewers, shared by the board and the fight pages. */
public final class Wording {
    private Wording() {}

    /** "2. Brutus hits Vex for 14 · Brutus dodges": red's attack on blue, then blue's on red. */
    public static String logLine(RoundView round, String red, String blue) {
        String redAttack = round.redHit() ? red + " hits " + blue + " for " + round.redDamage() : blue + " dodges";
        String blueAttack = round.blueHit() ? blue + " hits " + red + " for " + round.blueDamage() : red + " dodges";
        return round.n() + ". " + redAttack + " · " + blueAttack;
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
