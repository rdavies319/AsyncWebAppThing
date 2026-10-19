package com.rdavies.api.web;

import com.rdavies.api.prediction.Prediction;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON API from spec section 8. Errors are ProblemDetails, see {@link GameApiExceptionHandler}. */
@RestController
public class GameApiController {
    private final RoundService roundService;
    private final PredictionService predictionService;
    private final CurrentUser currentUser;

    public GameApiController(RoundService roundService, PredictionService predictionService, CurrentUser currentUser) {
        this.roundService = roundService;
        this.predictionService = predictionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/rounds/current")
    public RoundView currentRound() {
        return roundService.currentView().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No round is open yet"));
    }

    public record PlacePrediction(Long roundId, PredictionType direction, Long stake) {}

    @PostMapping("/predictions")
    @ResponseStatus(HttpStatus.CREATED)
    public Prediction placePrediction(@RequestBody PlacePrediction request) {
        return predictionService.placePrediction(currentUser.id(), request.roundId(), request.direction(), request.stake());
    }
}
