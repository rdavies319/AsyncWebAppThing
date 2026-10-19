package com.rdavies.api.web;

import com.rdavies.api.GameProperties;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.wallet.WalletService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class GameController {
    private final RoundService roundService;
    private final PredictionService predictionService;
    private final WalletService walletService;
    private final GameProperties game;
    private final CurrentUser currentUser;

    public GameController(RoundService roundService, PredictionService predictionService,
                          WalletService walletService, GameProperties game, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.game = game;
        this.roundService = roundService;
        this.predictionService = predictionService;
        this.walletService = walletService;
    }

    @GetMapping("/")
    public String gameView(Model model) {
        roundService.getCurrentRound().ifPresent(round -> {
            model.addAttribute("round", round);
            // Epoch millis, so the page counts down by the server clock rather than the browser's
            model.addAttribute("rollAt", roundService.rollTime(round).toEpochMilli());
            model.addAttribute("deadline", roundService.predictionDeadline(round).toEpochMilli());
            predictionService.getPrediction(currentUser.id(), round.id()).ifPresent(p -> model.addAttribute("myPrediction", p));
        });
        roundService.getNextRollCommit().ifPresent(commit -> model.addAttribute("nextCommit", commit));
        predictionService.getLastResult(currentUser.id()).ifPresent(result -> model.addAttribute("lastResult", result));
        model.addAttribute("serverNow", roundService.now().toEpochMilli());
        model.addAttribute("minStake", game.minStake());
        model.addAttribute("wallet", walletService.getWallet(currentUser.id()));
        return "game";
    }

    @PostMapping("/predict")
    @ResponseBody
    public Map<String, String> predict(@RequestParam Long roundId,
                                      @RequestParam PredictionType prediction,
                                      @RequestParam Long stake) {
        try {
            predictionService.placePrediction(currentUser.id(), roundId, prediction, stake);
            return Map.of("status", "success");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
