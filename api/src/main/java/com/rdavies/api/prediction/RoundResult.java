package com.rdavies.api.prediction;

import com.rdavies.api.round.RoundService.SettledRound;

/** A settled round, and the player's prediction on it if they made one. */
public record RoundResult(SettledRound settled, Prediction prediction) {

    /** What happened to the player's stake, or null if they sat the round out. */
    public String outcome() {
        if (prediction == null) {
            return null;
        }
        return switch (prediction.status()) {
            case WON -> "You won " + prediction.payout() + " pts";
            case LOST -> "You lost " + prediction.stake() + " pts";
            case PUSH -> "Tie: your " + prediction.stake() + " pts were refunded";
            case PENDING -> null;
        };
    }
}
