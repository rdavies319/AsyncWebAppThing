package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.records.Fight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The engine row-locks the fights it works on (spec section 7), so a bet can't slip in while a
 * fight starts. Not @Transactional: the locks are taken and tested from separate transactions.
 */
@GameTest
class FightRowLockTests {
    @Autowired TestGame game;
    @Autowired TestFights testFights;
    @Autowired FightRepository fights;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clear() {
        testFights.deleteAll();
    }

    @Test
    void lockAnnouncedHoldsTheRowUntilTheTransactionEnds() throws Exception {
        long id = game.boot().announced().fightId();

        assertRowLockedWhileHeld(id, fights::lockAnnounced);
    }

    @Test
    void lockLiveHoldsTheRowUntilTheTransactionEnds() throws Exception {
        game.boot();
        long id = game.startFight().fightId();

        assertRowLockedWhileHeld(id, fights::lockLive);
    }

    private void assertRowLockedWhileHeld(long id, Supplier<Optional<Fight>> lock) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<Fight>> holder = other.submit(() -> transactions.execute(tx -> {
                Optional<Fight> fight = lock.get();
                locked.countDown();
                await(release);
                return fight;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "the other transaction took the lock");

            assertFalse(tryLockRow(id), "the row is locked while the other transaction runs");

            release.countDown();
            assertEquals(id, holder.get(10, TimeUnit.SECONDS).orElseThrow().id());
            assertTrue(tryLockRow(id), "the lock ends with the transaction");
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    private boolean tryLockRow(long id) {
        try {
            transactions.executeWithoutResult(tx ->
                    jdbc.queryForList("SELECT id FROM fights WHERE id = ? FOR UPDATE NOWAIT", id));
            return true;
        } catch (DataAccessException e) {
            return false;
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
