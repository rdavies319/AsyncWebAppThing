package com.rdavies.arena.fight;

import com.rdavies.arena.DevGameTest;
import com.rdavies.arena.GameProperties;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.fight.records.ExchangeResult;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.SimulatedFight;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forcing only pulls due times forward, so a forced fight plays out exactly as an unforced fight on
 * the same seed: as the combat core simulates it (FightEngineTests checks the unforced side).
 */
@DevGameTest
@Transactional
class ForcedFightTests {
    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired GameProperties gameProperties;
    @Autowired MutableClock clock;
    @Autowired FightService fightService;
    @Autowired FightRepository fights;
    @Autowired FightExchangeRepository exchanges;
    @Autowired GladiatorService gladiators;
    @Autowired UserService userService;

    @Test
    void aForcedFightEndsAsItsSeedSays() throws Exception {
        GamePrincipal dev = TestPlayers.signUp(userService, "forcer");
        long id = game.boot().announced().fightId();
        clock.set(Instant.parse("2026-01-01T00:05:00Z"));

        mockMvc.perform(post("/dev/fights/start").with(user(dev)).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/dev/fights/exchange").with(user(dev)).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/dev/fights/finish").with(user(dev)).with(csrf())).andExpect(status().isOk());

        FightResult result = fightService.result(id);
        Fight fight = fights.findById(id).orElseThrow();
        SimulatedFight simulated = Combat.simulate(fight.seed(), id,
                gladiators.stats(fight.redId()), gladiators.stats(fight.blueId()), gameProperties.maxExchanges());
        assertEquals(simulated.outcome().winner(), result.winner());
        assertEquals(simulated.outcome().endedBy(), result.endedBy());
        List<ExchangeResult> stored = exchanges.findByFight(id).stream().map(e -> e.result()).toList();
        assertEquals(simulated.exchanges(), stored);
    }
}
