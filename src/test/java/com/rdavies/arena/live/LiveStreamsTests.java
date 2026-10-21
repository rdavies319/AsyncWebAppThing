package com.rdavies.arena.live;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveStreamsTests {
    private final LiveStreams streams = new LiveStreams();

    @Test
    void broadcastsReachEveryStream() {
        RecordingEmitter alice = subscribe(1);
        RecordingEmitter bob = subscribe(2);

        streams.broadcast("exchange", "r1");

        assertEquals(List.of("event:exchange\ndata:r1\n\n"), alice.sent);
        assertEquals(alice.sent, bob.sent);
    }

    @Test
    void personalEventsReachOnlyThatPlayersStreams() {
        RecordingEmitter aliceTab1 = subscribe(1);
        RecordingEmitter aliceTab2 = subscribe(1);
        RecordingEmitter bob = subscribe(2);

        streams.sendToUser(1, "bet_settled", "won");

        assertEquals(1, aliceTab1.sent.size());
        assertEquals(1, aliceTab2.sent.size());
        assertTrue(bob.sent.isEmpty());
    }

    @Test
    void aStreamThatFailsToSendIsDropped() {
        RecordingEmitter gone = subscribe(1);
        RecordingEmitter live = subscribe(2);
        gone.broken = true;

        streams.broadcast("exchange", "r1");
        streams.broadcast("exchange", "r2");

        assertEquals(1, streams.size());
        assertEquals(2, live.sent.size());
    }

    @Test
    void aDroppedStreamGetsNoMorePersonalEvents() {
        RecordingEmitter gone = subscribe(1);
        gone.broken = true;
        streams.sendToUser(1, "bet_settled", "won");
        gone.broken = false;

        streams.sendToUser(1, "bet_settled", "won");

        assertTrue(gone.sent.isEmpty());
        assertEquals(0, streams.size());
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
}
