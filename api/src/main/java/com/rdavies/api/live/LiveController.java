package com.rdavies.api.live;

import com.rdavies.api.prediction.PredictionSettled;
import com.rdavies.api.round.RoundOpened;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundSettled;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.user.GamePrincipal;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Pushes round changes to game pages over Server-Sent Events (spec section 8). Events carry state,
 * not ticks: the page runs its countdown from server_time and the round's cutoff_at. Every connect,
 * including a browser reconnect, starts with a snapshot, so missed events never need replaying.
 */
@RestController
public class LiveController {
    private final LiveStreams streams;
    private final RoundService roundService;
    private final CurrentUser currentUser;

    public LiveController(LiveStreams streams, RoundService roundService, CurrentUser currentUser) {
        this.streams = streams;
        this.roundService = roundService;
        this.currentUser = currentUser;
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() throws IOException {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; the browser reconnects if it drops
        // Sent before the handler returns, these are buffered and flushed first
        emitter.send(SseEmitter.event().name("server_time").data(serverTime()));
        roundService.currentView().ifPresent(view -> {
            try {
                emitter.send(SseEmitter.event().name("round_open").data(view));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        streams.add(currentUser.principal().map(GamePrincipal::id).orElse(null), emitter);
        return emitter;
    }

    // After commit, so a client that refetches on these events sees the new state.
    // The engine publishes RoundSettled, then the prediction batch, then RoundOpened.

    @TransactionalEventListener
    public void onRoundSettled(RoundSettled event) {
        streams.broadcast("round_settled", event);
    }

    @TransactionalEventListener
    public void onPredictionsSettled(PredictionSettled.Batch batch) {
        for (PredictionSettled settled : batch.predictions()) {
            streams.sendToUser(settled.userId(), "prediction_settled", settled);
        }
    }

    @TransactionalEventListener
    public void onRoundOpened(RoundOpened event) {
        streams.broadcast("server_time", serverTime());
        roundService.currentView().ifPresent(view -> streams.broadcast("round_open", view));
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        streams.heartbeat();
    }

    private Map<String, Instant> serverTime() {
        return Map.of("server_time", roundService.now());
    }
}
