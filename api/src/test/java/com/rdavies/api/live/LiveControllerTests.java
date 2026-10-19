package com.rdavies.api.live;

import com.rdavies.api.GameTest;
import com.rdavies.api.prediction.PredictionSettled;
import com.rdavies.api.prediction.PredictionStatus;
import com.rdavies.api.round.RoundRolled;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundSettled;
import com.rdavies.api.round.RoundView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a roll reaches the streams (spec section 8), by handing the live module a RoundRolled
 * directly. RoundEventsCommitTests covers the event only being delivered after the tick commits.
 */
@GameTest
class LiveControllerTests {
    private static final Instant TICKED_AT = Instant.parse("2000-01-01T00:00:00.200Z");

    @Autowired LiveController liveController;
    @Autowired LiveStreams streams;
    @Autowired RoundService roundService;
    private final List<RecordingEmitter> subscribed = new ArrayList<>();

    @AfterEach
    void dropStreams() {
        subscribed.forEach(emitter -> emitter.broken = true);
        streams.heartbeat(); // a failed send removes each one
    }

    @Test
    void aRollIsSentAsSettledThenEachPlayersResultThenTheNewRound() {
        RecordingEmitter anonymous = subscribe(null);
        RecordingEmitter alice = subscribe(101L);
        RecordingEmitter bob = subscribe(102L);
        RecordingEmitter carol = subscribe(103L); // logged in, but didn't predict

        liveController.send(new RoundRolled(new RoundSettled(6, 7, 42, "seed-7"), List.of(
                new PredictionSettled(101, 1001, 6, PredictionStatus.WON, 196, 1096),
                new PredictionSettled(102, 1002, 6, PredictionStatus.LOST, 0, 900)), view()));

        List<String> broadcastOnly = List.of("round_settled", "server_time", "round_open");
        assertEquals(broadcastOnly, anonymous.eventNames());
        assertEquals(broadcastOnly, carol.eventNames());
        assertEquals(List.of("round_settled", "prediction_settled", "server_time", "round_open"), alice.eventNames());
        assertEquals(alice.eventNames(), bob.eventNames());

        assertEquals("event:round_settled\ndata:{\"round_id\":6,\"next_round_id\":7,\"next_roll\":42,\"next_round_seed\":\"seed-7\"}\n\n",
                anonymous.sent.get(0));
        assertTrue(alice.sent.get(1).contains("\"prediction_id\":1001,\"round_id\":6,\"status\":\"won\",\"payout\":196,\"balance\":1096"),
                alice.sent.get(1));
        assertTrue(bob.sent.get(1).contains("\"prediction_id\":1002"), bob.sent.get(1));
    }

    @Test
    void theNewRoundIsStampedWithTheTimeItIsSent() {
        RecordingEmitter viewer = subscribe(null);

        liveController.send(new RoundRolled(new RoundSettled(6, 7, 42, "seed-7"), List.of(), view()));

        String sentAt = roundService.now().toString();
        String roundOpen = viewer.sent.get(2);
        assertTrue(roundOpen.startsWith("event:round_open\ndata:{\"round_id\":7,\"roll\":42,"), roundOpen);
        assertTrue(roundOpen.contains("\"server_time\":\"" + sentAt + "\""), roundOpen);
        assertFalse(roundOpen.contains(TICKED_AT.toString()), roundOpen);
        assertTrue(viewer.sent.get(1).contains("\"server_time\":\"" + sentAt + "\""), viewer.sent.get(1));
    }

    @Test
    void theFirstRoundOpensWithNothingSettled() {
        RecordingEmitter viewer = subscribe(null);

        liveController.send(new RoundRolled(null, List.of(), view()));

        assertEquals(List.of("server_time", "round_open"), viewer.eventNames());
    }

    private RecordingEmitter subscribe(Long userId) {
        RecordingEmitter emitter = new RecordingEmitter();
        streams.add(userId, emitter);
        subscribed.add(emitter);
        return emitter;
    }

    /** Round 7 as the engine opened it, stamped with the tick's time rather than the send time. */
    private static RoundView view() {
        Instant start = Instant.parse("2000-01-01T00:00:00Z");
        return new RoundView(7, 42, start, start.plusSeconds(50),
                new RoundView.Side(16_896, 500), new RoundView.Side(23_902, 418), "commit-8", TICKED_AT);
    }
}
