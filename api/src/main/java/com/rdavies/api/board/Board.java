package com.rdavies.api.board;

import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.RoundView;

import java.util.List;

/**
 * What a viewer sees on the game page: the open round, the last roll, and for a logged-in player
 * their bet, balance and top-up. Built by BoardService; the template only renders it.
 *
 * @param round        the open round with its odds and countdown times; null until the first round opens
 * @param lastRoll     the most recently settled round; null until one has settled
 * @param player       the logged-in player's part of the board; null for an anonymous viewer
 * @param stakeOptions the stakes offered as buttons
 */
public record Board(RoundView round, LastRoll lastRoll, PlayerBoard player, List<Long> stakeOptions) {

    /** Whether the prediction form shows: a logged-in player, an open round, and no bet on it yet. */
    public boolean canPredict() {
        return player != null && round != null && player.bet() == null;
    }

    /**
     * The last settled round: its roll, the roll it was settled against, and what happened to the
     * player's stake.
     *
     * @param outcome e.g. "You won 196 pts"; null if the viewer is anonymous or sat the round out
     */
    public record LastRoll(int roll, int nextRoll, String outcome) {}

    /**
     * @param bet         the player's bet on the open round; null if they haven't placed one
     * @param topUpTarget the balance the daily top-up would refill to; null if it can't be claimed now
     */
    public record PlayerBoard(String name, long balance, Bet bet, Long topUpTarget) {}

    /** @param multiplier the multiplier locked in when placed, for display, e.g. "1.96x" */
    public record Bet(long stake, PredictionType direction, String multiplier) {}
}
