package com.rdavies.api.live;

import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.user.records.GamePrincipal;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes game changes to game pages over Server-Sent Events. Events carry state, not ticks: the
 * page runs its countdowns from server_time. Every connect, including a browser reconnect, starts
 * with a snapshot, so missed events never need replaying.
 *
 * <p>Writing to a stream blocks until the client's socket takes the bytes, so a slow client can
 * stall a send. Sends therefore run on one dedicated thread, never the engine's or the scheduler's:
 * a stalled client can delay other clients' events, but never the engine. One thread also keeps
 * events in the order they were published.
 */
@RestController
public class LiveController {
    private final LiveStreams streams;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ExecutorService sender =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("live-sender").daemon().factory());

    public LiveController(LiveStreams streams, CurrentUser currentUser, Clock clock) {
        this.streams = streams;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() throws IOException {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; the browser reconnects if it drops
        // Sent before the handler returns, so it is buffered and flushed first
        emitter.send(SseEmitter.event().name("server_time").data(serverTime(clock.instant())));
        streams.add(currentUser.principal().map(GamePrincipal::id).orElse(null), emitter);
        return emitter;
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
