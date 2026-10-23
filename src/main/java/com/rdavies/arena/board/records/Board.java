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

    /** A multiplier in basis points as shown, e.g. "1.96x": rounded down, so it never promises more than a win pays. */
    public static String odds(int multiplierBps) {
        return multiplierBps / 10_000 + "." + String.format("%02d", multiplierBps % 10_000 / 100) + "x";
    }

    /** Nothing has been announced yet, so there is no arena to show. */
    public boolean openingSoon() {
        return live == null && lastResult == null && next == null;
    }

    /** @param log one line per round, newest first */
    public record Live(long fightId, Corner red, Corner blue, Instant nextRoundAt, List<String> log) {
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

    /**
     * @param cutoffAt    betting closes here
     * @param bettingOpen whether betting was still open when the board was built
     */
    public record Next(long fightId, Contender red, Contender blue, Instant startsAt, Instant cutoffAt,
                       boolean bettingOpen) {}

    /**
     * @param multiplierBps what a winning bet on this corner pays per point staked, in basis points
     * @param maxStake      the largest stake on this corner
     */
    public record Contender(String name, String epithet, int elo, int multiplierBps, long maxStake) {
        /** The multiplier as shown, e.g. "1.96x": rounded down, so it never promises more than a win pays. */
        public String odds() {
            return Board.odds(multiplierBps);
        }
    }

    /**
     * @param topUpTarget the balance the daily top-up would refill to; null if it can't be claimed now
     * @param betForm     the stakes the player can bet on the next fight; null once they have bet, or betting has closed
     * @param nextBet     the player's bet on the next fight, e.g. "Your bet: 100 on Vex at 1.96x"; null if none
     * @param fightBet    the player's bet on the fight now showing (live, or the last result); null if none
     * @param recentBets  the player's last 5 bets, newest first
     */
    public record PlayerBoard(String name, long balance, Long topUpTarget, BetForm betForm, String nextBet,
                              String fightBet, List<PastBet> recentBets) {
        public PlayerBoard {
            recentBets = List.copyOf(recentBets);
        }
    }

    /** @param line e.g. "Brutus v Vex: 100 on Brutus at 1.96x · won 196" */
    public record PastBet(long fightId, String line) {}

    /** The stake buttons for each corner: the stake options the player can afford, within the corner's max stake. */
    public record BetForm(long fightId, List<Long> redStakes, List<Long> blueStakes) {
        public BetForm {
            redStakes = List.copyOf(redStakes);
            blueStakes = List.copyOf(blueStakes);
        }

        public boolean nothingAffordable() {
            return redStakes.isEmpty() && blueStakes.isEmpty();
        }
    }
}
