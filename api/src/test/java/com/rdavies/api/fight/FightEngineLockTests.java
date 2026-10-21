package com.rdavies.api.fight;

import com.rdavies.api.GameTest;
import com.rdavies.api.TestGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One tick at a time (spec section 7): while one transaction holds the arena's advisory lock, a
 * second tick skips. Not @Transactional: the test transaction would hold the lock itself.
 */
@GameTest
class FightEngineLockTests {
    @Autowired TestGame game;
    @Autowired FightService fightService;
    @Autowired TestFights testFights;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clear() {
        testFights.deleteAll();
    }

    @Test
    void aTickSkipsWhileAnotherHoldsTheLock() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = other.submit(() -> transactions.executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, FightEngine.LOCK_KEY);
                locked.countDown();
                await(release);
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "the other transaction took the lock");

            assertNull(game.boot().announced(), "the tick skipped, so nothing was announced");

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertNotNull(game.boot().announced(), "with the lock free, the tick runs");
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
