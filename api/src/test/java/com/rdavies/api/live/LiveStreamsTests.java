package com.rdavies.api.live;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LiveStreamsTests {
    private final LiveStreams streams = new LiveStreams();

    @Test
    void broadcastsReachEveryStream() {
        RecordingEmitter alice = subscribe(1);
        RecordingEmitter bob = subscribe(2);

        streams.broadcast("round_open", "r1");

        assertEquals(List.of("event:round_open\ndata:r1\n\n"), alice.sent);
        assertEquals(alice.sent, bob.sent);
    }

    @Test
    void personalEventsReachOnlyThatPlayersStreams() {
        RecordingEmitter aliceTab1 = subscribe(1);
        RecordingEmitter aliceTab2 = subscribe(1);
        RecordingEmitter bob = subscribe(2);

        streams.sendToUser(1, "prediction_settled", "won");

        assertEquals(1, aliceTab1.sent.size());
        assertEquals(1, aliceTab2.sent.size());
        assertTrue(bob.sent.isEmpty());
    }

    @Test
    void aStreamThatFailsToSendIsDropped() {
        RecordingEmitter gone = subscribe(1);
        RecordingEmitter live = subscribe(2);
        gone.broken = true;

        streams.broadcast("round_open", "r1");
        streams.broadcast("round_open", "r2");

        assertEquals(1, streams.size());
        assertEquals(2, live.sent.size());
    }

    @Test
    void heartbeatIsAComment() {
        RecordingEmitter emitter = subscribe(1);
        streams.heartbeat();
        assertEquals(List.of(":keepalive\n\n"), emitter.sent);
    }

    private RecordingEmitter subscribe(long userId) {
        RecordingEmitter emitter = new RecordingEmitter();
        streams.add(userId, emitter);
        return emitter;
    }

    /** Captures each event as SSE wire text instead of writing to a response. */
    static class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean broken;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("client gone");
            }
            sent.add(builder.build().stream().map(part -> String.valueOf(part.getData())).collect(Collectors.joining()));
        }
    }
}
