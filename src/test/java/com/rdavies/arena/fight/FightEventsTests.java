package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.events.RoundFought;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.fight.events.FightStarted;
import com.rdavies.arena.fight.events.MatchupAnnounced;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The engine's events (spec section 9) are published inside the tick and delivered only after it
 * commits. Not @Transactional, so each tick really commits; the fights are removed after each test.
 */
@GameTest
class FightEventsTests {
    @Autowired TestGame game;
    @Autowired FightEngine engine;
    @Autowired TestFights testFights;
    @Autowired TestFightEvents events;
    @Autowired TransactionTemplate transactions;

    @BeforeEach
    @AfterEach
    void clear() {
        testFights.deleteAll();
        events.delivered.clear();
    }

    @Test
    void bootstrapAnnouncesTheFirstMatchup() {
        game.boot();

        MatchupAnnounced announced = (MatchupAnnounced) events.delivered.getFirst();
        assertEquals(1, events.delivered.size());
        assertEquals(Instant.parse("2026-01-01T01:00:00Z"), announced.fight().startsAt());
    }

    @Test
    void aStartAnnouncesTheNextMatchupInTheSameTick() {
        game.boot();
        events.delivered.clear();

        LiveFight live = game.startFight();

        assertEquals(List.of(FightStarted.class, MatchupAnnounced.class), events.types());
        FightStarted started = (FightStarted) events.delivered.getFirst();
        assertEquals(live.fightId(), started.fightId());
        assertEquals(live.red(), started.red());
        assertEquals(live.red().startingHp(), started.red().startingHp());
    }

    @Test
    void eachRoundAndTheResultAreDelivered() {
        game.plantFight(Winner.RED, EndedBy.KO);
        LiveFight live = game.startFight();
        events.delivered.clear();

        FightResult result = game.finishFight();

        List<Class<?>> types = events.types();
        assertEquals(result.roundsDone() + 1, types.size());
        assertTrue(types.subList(0, result.roundsDone()).stream().allMatch(RoundFought.class::equals), types.toString());
        RoundFought first = (RoundFought) events.delivered.getFirst();
        assertEquals(live.fightId(), first.fightId());
        assertEquals(1, first.n());
        // Both started on 1000, so the win is worth half of K = 32
        assertEquals(new FightSettled(live.fightId(), Winner.RED, EndedBy.KO, 1016, 984), events.delivered.getLast());
    }

    @Test
    void aRolledBackTickDeliversNothing() {
        transactions.executeWithoutResult(tx -> {
            engine.tick(); // would bootstrap, and announce a matchup
            tx.setRollbackOnly();
        });

        assertTrue(events.delivered.isEmpty(), events.delivered.toString());
    }
}
