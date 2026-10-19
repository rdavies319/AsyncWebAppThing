package com.rdavies.api.web;

import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.RoundService;
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

    public GameController(RoundService roundService, PredictionService predictionService,
                          WalletService walletService) {
        this.roundService = roundService;
        this.predictionService = predictionService;
        this.walletService = walletService;
    }

    @GetMapping("/")
    public String gameView(Model model) {
        // For MVP, we'll use a dummy user ID 1
        roundService.getCurrentRound().ifPresent(round -> {
            model.addAttribute("round", round);
            // Epoch millis, so the page counts down by the server clock rather than the browser's
            model.addAttribute("rollAt", roundService.rollTime(round).toEpochMilli());
            model.addAttribute("deadline", roundService.predictionDeadline(round).toEpochMilli());
            predictionService.getPrediction(1L, round.roundId()).ifPresent(p -> model.addAttribute("myPrediction", p));
        });
        predictionService.getLastResult(1L).ifPresent(result -> model.addAttribute("lastResult", result));
        model.addAttribute("serverNow", roundService.now().toEpochMilli());
        model.addAttribute("wallet", walletService.getWallet(1L));
        return "game";
    }

    @PostMapping("/predict")
    @ResponseBody
    public Map<String, String> predict(@RequestParam Long roundId,
                                      @RequestParam PredictionType prediction,
                                      @RequestParam Long stake) {
        try {
            // Dummy user ID 1
            predictionService.placePrediction(1L, roundId, prediction, stake);
            return Map.of("status", "success");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
