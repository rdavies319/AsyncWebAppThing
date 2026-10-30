package com.rdavies.arena.live;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.TestFights;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every arena event reaches every open stream, with the payload from spec section 9, once its tick
 * has committed. Not @Transactional, so each tick really commits; the fights are removed after each test.
 */
@GameTest
class ArenaBroadcastTests {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired TestGame game;
    @Autowired TestFights testFights;
    @Autowired LiveStreams streams;

    RecordingEmitter viewer;

    @BeforeEach
    void connect() {
        testFights.deleteAll();
        viewer = new RecordingEmitter();
        streams.add(null, viewer); // anonymous: arena events are broadcasts
    }

    @AfterEach
    void disconnect() {
        viewer.broken = true; // dropped at the next send
        testFights.deleteAll();
    }

    @Test
    void anAnnouncementSendsTheMatchup() throws Exception {
        AnnouncedFight announced = game.boot().announced();

        viewer.awaitEvents(1, WAIT);
        assertEquals(List.of("matchup_announced"), viewer.eventNames());
        String data = viewer.sent.getFirst();
        assertTrue(data.contains("\"fight_id\":" + announced.fightId()), data);
        assertTrue(data.contains("\"starts_at\":\"2026-01-01T01:00:00Z\""), data);
        assertTrue(data.contains("\"red\":{\"slug\":\"" + announced.red().slug() + "\""), data);
        assertTrue(data.contains("\"elo\":1000"), data);
    }

    @Test
    void aStartSendsTheCornersWithStartingHpThenTheNextMatchup() throws Exception {
        game.boot();
        viewer.awaitEvents(1, WAIT);

        LiveFight live = game.startFight();

        viewer.awaitEvents(3, WAIT);
        assertEquals(List.of("matchup_announced", "fight_started", "matchup_announced"), viewer.eventNames());
        String data = viewer.sent.get(1);
        assertTrue(data.startsWith("event:fight_started\ndata:{\"fight_id\":" + live.fightId() + ","), data);
        assertTrue(data.contains("\"starting_hp\":" + live.red().startingHp()), data);
        assertTrue(data.contains("\"name\":\"" + live.blue().name() + "\""), data);
    }

    @Test
    void eachRoundSendsWhoHitAndTheHpAfter() throws Exception {
        LiveFight live = game.startFight();
        viewer.awaitEvents(3, WAIT);

        LiveFight after = game.nextRound().live();

        viewer.awaitEvents(4, WAIT);
        assertEquals("round", viewer.eventNames().getLast());
        String data = viewer.sent.getLast();
        assertTrue(data.startsWith("event:round\ndata:{\"fight_id\":" + live.fightId() + ",\"n\":1,"), data);
        assertTrue(data.contains("\"red_hp_after\":" + after.red().currentHp()), data);
        assertTrue(data.contains("\"blue_hp_after\":" + after.blue().currentHp()), data);
        assertTrue(data.contains("\"red_hit\":"), data);
        assertFalse(data.contains("_roll"), data);
    }

    @Test
    void anAnnouncementSendsEachCornersOdds() throws Exception {
        game.boot();

        viewer.awaitEvents(1, WAIT);
        String data = viewer.sent.getFirst();
        assertTrue(data.contains("\"elo\":1000,\"multiplier_bps\":19600,\"stakes\":[10,100,500]}"), data);
    }

    @Test
    void aSettledFightSendsTheResultAndTheNewRatings() throws Exception {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        game.startFight();

        FightResult result = game.finishFight();

        int expected = 3 + result.roundsDone() + 1;
        viewer.awaitEvents(expected, WAIT);
        assertEquals(result.roundsDone(), Collections.frequency(viewer.eventNames(), "round"));
        assertEquals("event:fight_settled\ndata:{\"fight_id\":" + result.fightId()
                + ",\"winner\":\"blue\",\"ended_by\":\"ko\",\"red_elo\":984,\"blue_elo\":1016}\n\n", viewer.sent.getLast());
    }
}
