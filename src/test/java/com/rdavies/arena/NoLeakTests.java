package com.rdavies.arena;

import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.TestFights;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import com.rdavies.arena.live.LiveStreams;
import com.rdavies.arena.live.RecordingEmitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No public response carries a hidden stat, the seed or a raw roll (spec section 3, Hidden stats):
 * the board, every public page and JSON response, and every SSE payload of a whole fight. Not
 * @Transactional, so the fight's events really go out; the fights are removed after.
 */
@GameTest
class NoLeakTests {
    /** Field names that would leak a stat, the seed or a raw roll. */
    private static final List<String> INTERNAL = List.of(
            "\"hp\"", "attack_min", "attack_max", "attackMin", "attackMax", "defence", "agility", "seed", "_roll", "Roll");

    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired TestFights testFights;
    @Autowired FightService fightService;
    @Autowired GladiatorService gladiators;
    @Autowired LiveStreams streams;

    RecordingEmitter viewer;

    @BeforeEach
    void connect() {
        testFights.deleteAll();
        viewer = new RecordingEmitter();
        streams.add(null, viewer);
    }

    @AfterEach
    void disconnect() {
        viewer.broken = true;
        testFights.deleteAll();
    }

    @Test
    void noPublicResponseShowsAStatSeedOrRoll() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        game.startFight();
        game.nextRound();
        List<String> bodies = new ArrayList<>();
        bodies.add(body("/fragments/board", MediaType.TEXT_HTML)); // a fight live, the next announced
        bodies.add(body("/fights/current", MediaType.APPLICATION_JSON));
        FightResult result = game.finishFight();
        viewer.awaitEvents(3 + result.roundsDone() + 1, Duration.ofSeconds(5));
        bodies.addAll(viewer.sent);

        bodies.add(body("/fragments/board", MediaType.TEXT_HTML)); // between fights
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            bodies.add(body("/fights", type));
            bodies.add(body("/fights/" + result.fightId(), type));
            bodies.add(body("/gladiators", type));
            for (Gladiator g : gladiators.roster()) {
                bodies.add(body("/gladiators/" + g.slug(), type));
            }
        }

        for (String body : bodies) {
            for (String field : INTERNAL) {
                assertFalse(body.contains(field), field + " in " + body);
            }
        }
    }

    private String body(String path, MediaType type) throws Exception {
        return mockMvc.perform(get(path).accept(type)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
