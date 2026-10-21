package com.rdavies.api.fight;

import com.rdavies.api.GameTest;
import com.rdavies.api.TestGame;
import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;
import com.rdavies.api.fight.events.ExchangeRun;
import com.rdavies.api.fight.events.FightSettled;
import com.rdavies.api.fight.events.FightStarted;
import com.rdavies.api.fight.events.MatchupAnnounced;
import com.rdavies.api.fight.records.FightResult;
import com.rdavies.api.fight.records.LiveFight;
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
    void eachExchangeAndTheResultAreDelivered() {
        game.plantFight(Winner.RED, EndedBy.KO);
        LiveFight live = game.startFight();
        events.delivered.clear();

        FightResult result = game.finishFight();

        List<Class<?>> types = events.types();
        assertEquals(result.exchangesDone() + 1, types.size());
        assertTrue(types.subList(0, result.exchangesDone()).stream().allMatch(ExchangeRun.class::equals), types.toString());
        ExchangeRun first = (ExchangeRun) events.delivered.getFirst();
        assertEquals(live.fightId(), first.fightId());
        assertEquals(1, first.n());
        assertEquals(new FightSettled(live.fightId(), Winner.RED, EndedBy.KO), events.delivered.getLast());
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
