package com.rdavies.arena.fight;

import com.rdavies.arena.DevGameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A forced tick that finds the arena lock taken skips, like any tick, so the forcing call says so
 * rather than reporting a start that didn't happen. Not @Transactional: the test transaction would
 * hold the lock itself.
 */
@DevGameTest
class ForcingWhileBusyTests {
    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired TestFights testFights;
    @Autowired FightService fightService;
    @Autowired UserService userService;

    @BeforeEach
    @AfterEach
    void clear() {
        testFights.deleteAll(); // the player stays: the ledger is append-only, and its wallet still matches it
    }

    @Test
    void aForcedStartIsRefusedWhileAnotherTickHoldsTheLock() throws Exception {
        GamePrincipal dev = TestPlayers.signUp(userService, "busy");
        AnnouncedFight announced = game.boot().announced();
        testFights.whileEngineLocked(() ->
                mockMvc.perform(post("/dev/fights/start").with(user(dev)).with(csrf()))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.detail").value("The engine is busy; try again")));

        assertNull(fightService.current().live());
        assertEquals(announced.startsAt(), fightService.current().announced().startsAt(), "the pull forward rolled back");
    }
}
