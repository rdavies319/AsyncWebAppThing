package com.rdavies.api.prediction;

import com.rdavies.api.round.Odds;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundStatus;

/** A finished round, and the player's prediction on it if they made one. */
public record RoundResult(Round round, Prediction prediction) {
    public boolean voided() {
        return round.status() == RoundStatus.VOIDED;
    }

    /** What happened to the player's stake, or null if they sat the round out. */
    public String outcome() {
        if (prediction == null) {
            return null;
        }
        if (voided()) {
            return "Round voided: your " + prediction.stake() + " pts were refunded";
        }
        int prev = round.previousRoll();
        int roll = round.currentRoll();
        if (roll == prev) {
            return "Tie: your " + prediction.stake() + " pts were refunded";
        }
        boolean won = prediction.prediction() == PredictionType.HIGHER ? roll > prev : roll < prev;
        if (won) {
            return "You won " + Odds.payout(prev, prediction.prediction(), prediction.stake()) + " pts";
        }
        return "You lost " + prediction.stake() + " pts";
    }
}
