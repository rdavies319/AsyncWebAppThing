package com.rdavies.api.board;

/**
 * The outcome of the request that returned the board, shown inline on it.
 *
 * @param topUpError why a top-up was refused; null otherwise
 */
public record BoardFeedback(String topUpError) {
    public static BoardFeedback none() {
        return new BoardFeedback(null);
    }

    public static BoardFeedback topUpRefused(String error) {
        return new BoardFeedback(error);
    }
}
