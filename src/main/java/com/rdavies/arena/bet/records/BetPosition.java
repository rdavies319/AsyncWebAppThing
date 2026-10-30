package com.rdavies.arena.bet.records;

import com.rdavies.arena.fight.enums.Corner;

import java.util.List;

/**
 * What a player can do on the announced fight: the bet they have placed, the stakes open to them on
 * each corner, or nothing because betting has closed. Placing a bet accepts exactly what this offers.
 */
public sealed interface BetPosition {

    /** The player has bet on this fight; it stands after betting closes. */
    record Placed(Bet bet) implements BetPosition {}

    /**
     * Betting is open and the player hasn't bet: the corner's stakes that their balance covers.
     * Either list may be empty when the balance is below its smallest stake.
     */
    record Open(List<Long> red, List<Long> blue) implements BetPosition {
        public Open {
            red = List.copyOf(red);
            blue = List.copyOf(blue);
        }

        public List<Long> stakes(Corner corner) {
            return corner == Corner.RED ? red : blue;
        }
    }

    /** Betting has closed and the player didn't bet. */
    record Closed() implements BetPosition {}
}
