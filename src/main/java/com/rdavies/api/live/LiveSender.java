package com.rdavies.api.live;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The one live-sender thread every stream write runs on. Writing to a stream blocks until the
 * client's socket takes the bytes, so a slow client can stall a send: on this thread it can delay
 * other clients' events, but never the engine or the scheduler. One thread also keeps events in
 * the order they were published.
 */
@Component
class LiveSender {
    private final ExecutorService sender =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("live-sender").daemon().factory());

    void send(Runnable write) {
        sender.execute(write);
    }

    @PreDestroy
    void stop() {
        sender.shutdownNow();
    }
}
