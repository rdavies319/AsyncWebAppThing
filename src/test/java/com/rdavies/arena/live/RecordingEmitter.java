package com.rdavies.arena.live;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Captures each event as SSE wire text instead of writing to a response. Safe to read while the
 * live sender thread writes to it.
 */
public class RecordingEmitter extends SseEmitter {
    public final List<String> sent = new CopyOnWriteArrayList<>();
    /** When set, sends fail as if the client had gone, so LiveStreams drops this stream. */
    public volatile boolean broken;

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        if (broken) {
            throw new IOException("client gone");
        }
        sent.add(builder.build().stream().map(part -> String.valueOf(part.getData())).collect(Collectors.joining()));
    }

    /** The names of the events sent so far, in order, e.g. ["server_time", "exchange"]. */
    public List<String> eventNames() {
        return sent.stream().filter(s -> s.startsWith("event:"))
                .map(s -> s.substring("event:".length(), s.indexOf('\n'))).toList();
    }

    /** Waits for at least {@code count} events from the live sender thread. */
    public void awaitEvents(int count, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (eventNames().size() < count) {
            if (System.nanoTime() > deadline) {
                fail("expected " + count + " events within " + timeout + ", got " + eventNames());
            }
            Thread.sleep(10);
        }
    }
}
