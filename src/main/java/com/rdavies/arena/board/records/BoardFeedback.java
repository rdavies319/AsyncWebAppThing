package com.rdavies.arena.board.records;

/**
 * The outcome of the request that returned the board, shown inline on it.
 *
 * @param topUpError why a top-up was refused; null otherwise
 * @param betError   why a bet was refused; null otherwise
 */
public record BoardFeedback(String topUpError, String betError) {
    public static BoardFeedback none() {
        return new BoardFeedback(null, null);
    }

    public static BoardFeedback topUpRefused(String error) {
        return new BoardFeedback(error, null);
    }

    public static BoardFeedback betRefused(String error) {
        return new BoardFeedback(null, error);
    }
}
