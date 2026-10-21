package com.rdavies.api;

import com.rdavies.api.fight.records.AnnouncedFight;
import com.rdavies.api.fight.records.LiveFight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /fights/current (spec section 9): public, and never shows anything internal. */
@GameTest
@Transactional
class FightApiTests {
    /** Field names that would leak a stat, the seed or a raw roll. */
    private static final List<String> INTERNAL = List.of(
            "\"hp\"", "attack_min", "attack_max", "defence", "agility", "seed", "_roll");

    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;

    @Test
    void beforeTheFirstFightThereIsOnlyTheServerTime() throws Exception {
        clock.now = TestGame.BOOT;
        mockMvc.perform(get("/fights/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server_time").value("2026-01-01T00:00:30Z"))
                .andExpect(jsonPath("$.live").value(nullValue()))
                .andExpect(jsonPath("$.announced").value(nullValue()));
    }

    @Test
    void anyoneCanSeeTheAnnouncedFight() throws Exception {
        AnnouncedFight announced = game.boot().announced();
        mockMvc.perform(get("/fights/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announced.fight_id").value(announced.fightId()))
                .andExpect(jsonPath("$.announced.starts_at").value("2026-01-01T01:00:00Z"))
                .andExpect(jsonPath("$.announced.red.name").value(announced.red().name()))
                .andExpect(jsonPath("$.announced.red.epithet").value(announced.red().epithet()))
                .andExpect(jsonPath("$.announced.blue.slug").value(announced.blue().slug()))
                .andExpect(jsonPath("$.announced.red.elo").value(1000));
    }

    @Test
    void theLiveFightShowsHpExchangesAndTheNextExchangeTime() throws Exception {
        LiveFight live = game.startFight();
        game.nextExchange();

        mockMvc.perform(get("/fights/current"))
                .andExpect(jsonPath("$.live.fight_id").value(live.fightId()))
                .andExpect(jsonPath("$.live.red.name").value(live.red().name()))
                .andExpect(jsonPath("$.live.red.starting_hp").isNumber())
                .andExpect(jsonPath("$.live.red.current_hp").isNumber())
                .andExpect(jsonPath("$.live.exchanges_done").value(1))
                .andExpect(jsonPath("$.live.next_exchange_at").value("2026-01-01T01:02:00Z"))
                .andExpect(jsonPath("$.live.exchanges[0].n").value(1))
                .andExpect(jsonPath("$.live.exchanges[0].red_hit").isBoolean())
                .andExpect(jsonPath("$.live.exchanges[0].red_damage").isNumber())
                .andExpect(jsonPath("$.live.exchanges[0].blue_hp_after").isNumber())
                .andExpect(jsonPath("$.server_time").value("2026-01-01T01:01:00Z"));
    }

    @Test
    void noStatSeedOrRollIsEverShown() throws Exception {
        game.startFight();
        game.nextExchange();
        game.nextExchange();

        String body = mockMvc.perform(get("/fights/current")).andReturn().getResponse().getContentAsString();

        for (String field : INTERNAL) {
            assertFalse(body.contains(field), field + " in " + body);
        }
    }
}
