package com.rdavies.arena.board.records;

import java.time.Instant;
import java.util.List;

/**
 * What a viewer sees on the game page (spec section 10). Built by BoardService; the template only
 * renders it. Countdowns run from serverTime, so a page counts down by the server's clock.
 *
 * @param serverTime when the board was built
 * @param live       the fight happening now; null between fights
 * @param lastResult the last settled fight's result, shown between fights; null while one is live
 * @param next       the announced fight; null before bootstrap
 * @param player     the logged-in player's part of the board; null for an anonymous viewer
 * @param devPanel   whether to show the dev forcing buttons (dev profile, logged in)
 */
public record Board(Instant serverTime, Live live, Result lastResult, Next next, PlayerBoard player, boolean devPanel) {

    /** Nothing has been announced yet, so there is no arena to show. */
    public boolean openingSoon() {
        return live == null && lastResult == null && next == null;
    }

    /** @param log one line per exchange, newest first */
    public record Live(long fightId, Corner red, Corner blue, Instant nextExchangeAt, List<String> log) {
        public Live {
            log = List.copyOf(log);
        }
    }

    /** @param currentHp may be below 0 after the knockout blow; {@link #shownHp()} is what to display */
    public record Corner(String name, String epithet, int startingHp, int currentHp) {
        /** Current HP as shown: never below 0. */
        public int shownHp() {
            return Math.max(0, currentHp);
        }

        /** The HP bar's width: current HP as a share of starting HP, from 0 to 100. */
        public int hpPercent() {
            return shownHp() * 100 / startingHp;
        }
    }

    /** @param summary e.g. "Brutus beat Vex by KO" */
    public record Result(long fightId, String summary) {}

    public record Next(long fightId, Contender red, Contender blue, Instant startsAt) {}

    public record Contender(String name, String epithet, int elo) {}

    /** @param topUpTarget the balance the daily top-up would refill to; null if it can't be claimed now */
    public record PlayerBoard(String name, long balance, Long topUpTarget) {}
}
