package com.rdavies.api;

import com.rdavies.api.live.LiveStreams;
import com.rdavies.api.live.RecordingEmitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Live messages leave only after the tick commits. Not @Transactional, unlike most tests: a test
 * transaction never commits, so the after-commit listener would never run. Rows are committed, so
 * they are deleted after each test (no players, since the ledger is append-only).
 */
@GameTest
class RoundEventsCommitTests {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired TestGame game;
    @Autowired LiveStreams streams;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;
    private final RecordingEmitter viewer = new RecordingEmitter();

    @AfterEach
    void cleanUp() {
        viewer.broken = true;
        streams.heartbeat(); // the failed send removes the stream
        jdbc.update("DELETE FROM rounds");
    }

    @Test
    @Timeout(30)
    void aTickSendsItsMessagesOnlyOnceItCommits() throws InterruptedException {
        streams.add(null, viewer);

        // Rolled back: the round never happened, so nothing may be sent
        transaction.executeWithoutResult(status -> {
            game.open();
            status.setRollbackOnly();
        });
        // Committed: one sender thread keeps order, so had the rolled-back tick sent anything it would come first
        game.open();
        viewer.awaitEvents(2, WAIT);
        assertEquals(List.of("server_time", "round_open"), viewer.eventNames());

        game.rollNext();
        viewer.awaitEvents(5, WAIT);
        assertEquals(List.of("server_time", "round_open", "round_settled", "server_time", "round_open"),
                viewer.eventNames());
    }
}
