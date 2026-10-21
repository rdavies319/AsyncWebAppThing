package com.rdavies.arena.dev;

import com.rdavies.arena.DevGameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.board.BoardService;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dev forcing endpoints (spec section 7, Dev controls): each pulls a due time forward to now
 * and runs the real engine tick.
 */
@DevGameTest
@Transactional
class DevFightsTests {
    private static final Instant TEN_PAST_MIDNIGHT = Instant.parse("2026-01-01T00:10:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired FightService fightService;
    @Autowired BoardService boardService;
    @Autowired UserService userService;

    GamePrincipal dev;

    @BeforeEach
    void signUp() {
        dev = TestPlayers.signUp(userService, "dev");
    }

    @Test
    void startBeginsTheAnnouncedFightNow() throws Exception {
        long announced = game.boot().announced().fightId();
        clock.set(TEN_PAST_MIDNIGHT);

        mockMvc.perform(post("/dev/fights/start").with(user(dev)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live.fight_id").value(announced))
                .andExpect(jsonPath("$.live.starts_at").value("2026-01-01T00:10:00Z"));

        CurrentFights now = fightService.current();
        assertEquals(announced, now.live().fightId());
        assertEquals(Instant.parse("2026-01-01T01:00:00Z"), now.announced().startsAt()); // the next slot
    }

    @Test
    void startIsRefusedWhileAFightIsLive() throws Exception {
        game.startFight();

        mockMvc.perform(post("/dev/fights/start").with(user(dev)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A fight is already live"));
    }

    @Test
    void exchangeRunsTheNextExchangeNow() throws Exception {
        LiveFight live = game.startFight(); // at 01:00
        clock.set(live.startsAt().plusSeconds(10));

        mockMvc.perform(post("/dev/fights/exchange").with(user(dev)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live.exchanges_done").value(1))
                .andExpect(jsonPath("$.live.next_exchange_at").value("2026-01-01T01:02:00Z"));
    }

    @Test
    void exchangeIsRefusedWithoutALiveFight() throws Exception {
        game.boot();

        mockMvc.perform(post("/dev/fights/exchange").with(user(dev)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No fight is live"));
    }

    @Test
    void finishRunsExchangesUntilTheFightSettles() throws Exception {
        LiveFight live = game.startFight();
        clock.set(live.startsAt().plusSeconds(10));

        mockMvc.perform(post("/dev/fights/finish").with(user(dev)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").doesNotExist());

        assertNotNull(fightService.result(live.fightId()).winner());
    }

    @Test
    void theDevEndpointsNeedALoginAndCsrf() throws Exception {
        game.boot();

        mockMvc.perform(post("/dev/fights/start").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/dev/fights/start").with(user(dev))).andExpect(status().isForbidden());
    }

    @Test
    void aLoggedInPlayerSeesTheDevPanel() {
        assertTrue(boardService.board(Optional.of(dev)).devPanel());
        assertFalse(boardService.board(Optional.empty()).devPanel()); // its buttons need a login
    }
}
