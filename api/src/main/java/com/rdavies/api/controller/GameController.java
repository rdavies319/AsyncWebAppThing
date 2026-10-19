package com.rdavies.api.controller;

import com.rdavies.api.domain.*;
import com.rdavies.api.service.GameService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/")
    public String gameView(Model model) {
        // For MVP, we'll use a dummy user ID 1
        gameService.getCurrentRound().ifPresent(round -> {
            model.addAttribute("round", round);
            // Epoch millis, so the page counts down by the server clock rather than the browser's
            model.addAttribute("rollAt", gameService.rollTime(round).toEpochMilli());
            model.addAttribute("deadline", gameService.predictionDeadline(round).toEpochMilli());
            gameService.getPrediction(1L, round.roundId()).ifPresent(p -> model.addAttribute("myPrediction", p));
        });
        gameService.getLastResult(1L).ifPresent(result -> model.addAttribute("lastResult", result));
        model.addAttribute("serverNow", gameService.now().toEpochMilli());
        model.addAttribute("wallet", gameService.getWallet(1L));
        return "game";
    }

    @PostMapping("/predict")
    @ResponseBody
    public Map<String, String> predict(@RequestParam Long roundId,
                                      @RequestParam PredictionType prediction,
                                      @RequestParam Long stake) {
        try {
            // Dummy user ID 1
            gameService.placePrediction(1L, roundId, prediction, stake);
            return Map.of("status", "success");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
