package com.rdavies.api.live;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Open SSE streams, each tagged with the player it belongs to. In memory, so it only reaches clients
 * of this instance (spec section 6: fine for a single instance).
 */
@Component
public class LiveStreams {
    private record Stream(long userId, SseEmitter emitter) {}

    private final CopyOnWriteArrayList<Stream> streams = new CopyOnWriteArrayList<>();

    public void add(long userId, SseEmitter emitter) {
        Stream stream = new Stream(userId, emitter);
        streams.add(stream);
        emitter.onCompletion(() -> streams.remove(stream));
        emitter.onTimeout(() -> streams.remove(stream));
        emitter.onError(e -> streams.remove(stream));
    }

    public void broadcast(String event, Object data) {
        for (Stream stream : streams) {
            send(stream, SseEmitter.event().name(event).data(data));
        }
    }

    public void sendToUser(long userId, String event, Object data) {
        for (Stream stream : streams) {
            if (stream.userId() == userId) {
                send(stream, SseEmitter.event().name(event).data(data));
            }
        }
    }

    /** A comment line, so proxies don't close a stream that is idle between rounds. */
    public void heartbeat() {
        for (Stream stream : streams) {
            send(stream, SseEmitter.event().comment("keepalive"));
        }
    }

    int size() {
        return streams.size();
    }

    private void send(Stream stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            // The client has gone; the emitter's own callbacks may never fire
            streams.remove(stream);
        }
    }
}
