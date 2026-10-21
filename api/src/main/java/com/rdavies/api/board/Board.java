package com.rdavies.api.board;

/**
 * What a viewer sees on the game page. Until the arena opens, only a logged-in player's balance
 * and top-up. Built by BoardService; the template only renders it.
 *
 * @param player the logged-in player's part of the board; null for an anonymous viewer
 */
public record Board(PlayerBoard player) {

    /** @param topUpTarget the balance the daily top-up would refill to; null if it can't be claimed now */
    public record PlayerBoard(String name, long balance, Long topUpTarget) {}
}
