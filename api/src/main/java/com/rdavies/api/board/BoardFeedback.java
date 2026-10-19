package com.rdavies.api.board;

import com.rdavies.api.prediction.PredictionType;

/**
 * The outcome of the request that returned the board, shown inline on it. A refused bet keeps the
 * stake and side the player picked, so they can fix it and retry.
 *
 * @param placed     a bet was just placed
 * @param error      why a bet was refused; null otherwise
 * @param stake      the stake of the refused bet; null otherwise
 * @param side       the side of the refused bet; null otherwise
 * @param topUpError why a top-up was refused; null otherwise
 */
public record BoardFeedback(boolean placed, String error, Long stake, PredictionType side, String topUpError) {
    public static BoardFeedback none() {
        return new BoardFeedback(false, null, null, null, null);
    }

    public static BoardFeedback betPlaced() {
        return new BoardFeedback(true, null, null, null, null);
    }

    public static BoardFeedback betRefused(String error, Long stake, PredictionType side) {
        return new BoardFeedback(false, error, stake, side, null);
    }

    public static BoardFeedback topUpRefused(String error) {
        return new BoardFeedback(false, null, null, null, error);
    }

    /** The stake button to show as picked: the refused bet's stake, else the first option. */
    public long selectedStake(Board board) {
        return stake != null ? stake : board.stakeOptions().getFirst();
    }
}
