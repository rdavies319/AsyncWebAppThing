package com.rdavies.api.web;

import com.rdavies.api.GameProperties;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.wallet.Wallet;
import com.rdavies.api.wallet.WalletService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * The game page. htmx swaps the {@code board} fragment: refetched on every SSE round_open, and
 * returned by the prediction form so errors show inline. The JSON API is GameApiController.
 */
@Controller
public class GameController {
    private final RoundService roundService;
    private final PredictionService predictionService;
    private final WalletService walletService;
    private final GameProperties game;
    private final CurrentUser currentUser;

    public GameController(RoundService roundService, PredictionService predictionService,
                          WalletService walletService, GameProperties game, CurrentUser currentUser) {
        this.roundService = roundService;
        this.predictionService = predictionService;
        this.walletService = walletService;
        this.game = game;
        this.currentUser = currentUser;
    }

    @GetMapping("/")
    public String gameView(Model model) {
        addBoard(model);
        return "game";
    }

    @GetMapping("/fragments/board")
    public String board(Model model) {
        addBoard(model);
        return "game :: board";
    }

    @PostMapping("/predict")
    public String predict(@RequestParam Long roundId, @RequestParam PredictionType prediction,
                          @RequestParam(required = false) Long stake, Model model) {
        try {
            predictionService.placePrediction(currentUser.id(), roundId, prediction, stake);
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("stake", stake);
        }
        addBoard(model);
        return "game :: board";
    }

    @PostMapping("/topup")
    public String topUp(Model model) {
        try {
            walletService.claimTopUp(currentUser.id());
        } catch (IllegalStateException e) {
            model.addAttribute("topUpError", e.getMessage());
        }
        addBoard(model);
        return "game :: board";
    }

    /** Anyone can watch; the bet, last result and wallet are added only for a logged-in player. */
    private void addBoard(Model model) {
        Optional<GamePrincipal> player = currentUser.principal();
        roundService.getCurrentRound().ifPresent(round -> {
            model.addAttribute("round", round);
            // Epoch millis, so the page counts down by the server clock rather than the browser's
            model.addAttribute("rollAt", roundService.rollTime(round).toEpochMilli());
            model.addAttribute("deadline", roundService.predictionDeadline(round).toEpochMilli());
            player.flatMap(p -> predictionService.getPrediction(p.id(), round.id()))
                    .ifPresent(p -> model.addAttribute("myPrediction", p));
        });
        roundService.currentView().ifPresent(view -> model.addAttribute("view", view));
        model.addAttribute("serverNow", roundService.now().toEpochMilli());
        model.addAttribute("minStake", game.minStake());
        roundService.getLastSettledRound().ifPresent(last -> model.addAttribute("lastRound", last));
        player.ifPresent(p -> {
            model.addAttribute("player", p);
            predictionService.getLastResult(p.id()).ifPresent(result -> model.addAttribute("lastResult", result));
            Wallet wallet = walletService.getWallet(p.id());
            model.addAttribute("wallet", wallet);
            model.addAttribute("canTopUp", walletService.canTopUp(wallet));
            model.addAttribute("topUpTarget", game.topUpTarget());
        });
    }
}
