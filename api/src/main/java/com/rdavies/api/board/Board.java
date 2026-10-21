package com.rdavies.api.board;

import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.RoundView;

import java.util.List;
import java.util.Map;

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

    /** The sides this stake option can be placed on now, e.g. [HIGHER, LOWER]; empty for an anonymous viewer. */
    public List<PredictionType> sidesAllowing(long stake) {
        if (player == null) {
            return List.of();
        }
        return player.stakesBySide().entrySet().stream()
                .filter(side -> side.getValue().contains(stake)).map(Map.Entry::getKey).toList();
    }

    /** Whether this stake option can be placed on {@code side}, or on either side when {@code side} is null. */
    public boolean stakeAllowed(long stake, PredictionType side) {
        List<PredictionType> sides = sidesAllowing(stake);
        return side == null ? !sides.isEmpty() : sides.contains(side);
    }

    /**
     * @param bet          the player's bet on the open round; null if they haven't placed one
     * @param topUpTarget  the balance the daily top-up would refill to; null if it can't be claimed now
     * @param stakesBySide for each side that can win, the stake options the player may place on it:
     *                     within their balance and that side's max stake (lowered by the payout cap)
     */
    public record PlayerBoard(String name, long balance, Bet bet, Long topUpTarget,
                              Map<PredictionType, List<Long>> stakesBySide) {}

    /** @param multiplier the multiplier locked in when placed, for display, e.g. "1.96x" */
    public record Bet(long stake, PredictionType direction, String multiplier) {}
}
