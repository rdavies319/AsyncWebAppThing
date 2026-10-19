package com.rdavies.api.live;

import com.rdavies.api.prediction.PredictionSettled;
import com.rdavies.api.round.RoundOpened;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundSettled;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.user.GamePrincipal;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes round changes to game pages over Server-Sent Events (spec section 8). Events carry state,
 * not ticks: the page runs its countdown from server_time and the round's cutoff_at. Every connect,
 * including a browser reconnect, starts with a snapshot, so missed events never need replaying.
 *
 * <p>Writing to a stream blocks until the client's socket takes the bytes, so a slow client can
 * stall a send. Sends therefore run on one dedicated thread, never the engine's or the scheduler's:
 * a stalled client can delay other clients' events, but never the next roll. One thread also keeps
 * events in the order the engine published them.
 */
@RestController
public class LiveController {
    private final LiveStreams streams;
    private final RoundService roundService;
    private final CurrentUser currentUser;
    private final JsonMapper json;
    private final ExecutorService sender =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("live-sender").daemon().factory());

    public LiveController(LiveStreams streams, RoundService roundService, CurrentUser currentUser, JsonMapper json) {
        this.streams = streams;
        this.roundService = roundService;
        this.currentUser = currentUser;
        this.json = json;
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() throws IOException {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; the browser reconnects if it drops
        // Sent before the handler returns, these are buffered and flushed first
        emitter.send(SseEmitter.event().name("server_time").data(serverTime(roundService.now())));
        Optional<RoundView> view = roundService.currentView();
        if (view.isPresent()) {
            emitter.send(SseEmitter.event().name("round_open").data(view.get()));
        }
        streams.add(currentUser.principal().map(GamePrincipal::id).orElse(null), emitter);
        return emitter;
    }

    // After commit, so a client that refetches on these events sees the new state.
    // The engine publishes RoundSettled, then the prediction batch, then RoundOpened.

    @TransactionalEventListener
    public void onRoundSettled(RoundSettled event) {
        sender.execute(() -> streams.broadcast("round_settled", json.writeValueAsString(event)));
    }

    @TransactionalEventListener
    public void onPredictionsSettled(PredictionSettled.Batch batch) {
        sender.execute(() -> {
            for (PredictionSettled settled : batch.predictions()) {
                streams.sendToUser(settled.userId(), "prediction_settled", json.writeValueAsString(settled));
            }
        });
    }

    /** The event carries the round as the engine opened it, so this runs no queries. */
    @TransactionalEventListener
    public void onRoundOpened(RoundOpened event) {
        sender.execute(() -> {
            Instant now = roundService.now();
            streams.broadcast("server_time", json.writeValueAsString(serverTime(now)));
            streams.broadcast("round_open", json.writeValueAsString(event.view().at(now)));
        });
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        sender.execute(streams::heartbeat);
    }

    @PreDestroy
    void stop() {
        sender.shutdownNow();
    }

    private static Map<String, Instant> serverTime(Instant now) {
        return Map.of("server_time", now);
    }
}
