package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One tick at a time (spec section 7): while one transaction holds the arena's advisory lock, a
 * second tick skips. Not @Transactional: the test transaction would hold the lock itself.
 */
@GameTest
class FightEngineLockTests {
    @Autowired TestGame game;
    @Autowired FightEngine engine;
    @Autowired MutableClock clock;
    @Autowired FightService fightService;
    @Autowired TestFights testFights;

    @BeforeEach
    @AfterEach
    void clear() {
        testFights.deleteAll();
    }

    @Test
    void aTickSkipsWhileAnotherHoldsTheLock() throws Exception {
        testFights.whileEngineLocked(() -> {
            clock.set(TestGame.BOOT);
            assertFalse(engine.tick(), "the tick says it skipped");
            assertNull(fightService.current().announced(), "and changed nothing: no fight was announced");
        });

        assertTrue(engine.tick(), "with the lock free, the tick runs");
        assertNotNull(fightService.current().announced());
    }
}
