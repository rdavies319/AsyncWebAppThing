package com.rdavies.api.live;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open SSE streams, each tagged with the player it belongs to. In memory, so it only reaches clients
 * of this instance (spec section 6: fine for a single instance). Payloads arrive as JSON already, so
 * a broadcast serialises once rather than once per stream.
 */
@Component
public class LiveStreams {
    /** userId is null for an anonymous viewer, who gets broadcasts only. */
    private record Stream(Long userId, SseEmitter emitter) {}

    private final Set<Stream> streams = ConcurrentHashMap.newKeySet();
    /** The same streams by player, so a personal event is one lookup rather than a scan of every stream. */
    private final Map<Long, Set<Stream>> byUser = new ConcurrentHashMap<>();

    public void add(Long userId, SseEmitter emitter) {
        Stream stream = new Stream(userId, emitter);
        streams.add(stream);
        if (userId != null) {
            byUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(stream);
        }
        emitter.onCompletion(() -> remove(stream));
        emitter.onTimeout(() -> remove(stream));
        emitter.onError(e -> remove(stream));
    }

    public void broadcast(String event, String json) {
        for (Stream stream : streams) {
            send(stream, event(event, json));
        }
    }

    public void sendToUser(long userId, String event, String json) {
        for (Stream stream : byUser.getOrDefault(userId, Set.of())) {
            send(stream, event(event, json));
        }
    }

    /** A comment line, so proxies don't close a stream that is idle between fights. */
    public void heartbeat() {
        for (Stream stream : streams) {
            send(stream, SseEmitter.event().comment("keepalive"));
        }
    }

    int size() {
        return streams.size();
    }

    private static SseEmitter.SseEventBuilder event(String event, String json) {
        return SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON);
    }

    private void send(Stream stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            // The client has gone; the emitter's own callbacks may never fire
            remove(stream);
        }
    }

    private void remove(Stream stream) {
        streams.remove(stream);
        if (stream.userId() != null) {
            byUser.computeIfPresent(stream.userId(), (id, mine) -> {
                mine.remove(stream);
                return mine.isEmpty() ? null : mine;
            });
        }
    }
}
