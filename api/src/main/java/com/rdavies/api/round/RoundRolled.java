package com.rdavies.api.round;

import com.rdavies.api.prediction.PredictionSettled;

import java.util.List;

/**
 * Published by the round engine once per roll, with everything the roll changed: the round it
 * settled, each player's result, and the round it opened. Carries the state itself, so listeners
 * don't query for it after the tick commits.
 *
 * @param settled     the {@code round_settled} payload; null for the very first round, which settles nothing
 * @param predictions every prediction settled by this roll, one {@code prediction_settled} per player
 * @param opened      the newly opened round as clients see it, stamped with the tick's time
 */
public record RoundRolled(RoundSettled settled, List<PredictionSettled> predictions, RoundView opened) {
    /** The first round ever: opened, with nothing before it to settle. */
    static RoundRolled first(RoundView opened) {
        return new RoundRolled(null, List.of(), opened);
    }
}
