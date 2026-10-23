package com.rdavies.arena;

import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /gladiators and GET /gladiators/{slug} (spec section 9). CareerTests checks the figures. */
@GameTest
@Transactional
class GladiatorPagesTests {
    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired FightService fightService;

    @Test
    void theRosterIsPublicJson() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        AnnouncedFight fight = fightService.current().announced();
        game.finishFight();

        mockMvc.perform(get("/gladiators").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[?(@.slug == '" + fight.red().slug() + "')].wins").value(1))
                .andExpect(jsonPath("$[0].elo").isNumber())
                .andExpect(jsonPath("$[0].epithet").isString());
    }

    @Test
    void aGladiatorsRecordIsPublicJson() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        AnnouncedFight fight = fightService.current().announced();
        FightResult result = game.finishFight();

        mockMvc.perform(get("/gladiators/" + fight.red().slug()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(fight.red().name()))
                .andExpect(jsonPath("$.bio").isString())
                .andExpect(jsonPath("$.record.wins").value(1))
                .andExpect(jsonPath("$.record.win_rate_bps").value(10_000))
                .andExpect(jsonPath("$.record.ko_rate_bps").value(10_000))
                .andExpect(jsonPath("$.record.avg_damage_dealt_x100").isNumber())
                .andExpect(jsonPath("$.record.dodge_rate_bps").isNumber())
                .andExpect(jsonPath("$.head_to_head[0].opponent_slug").value(fight.blue().slug()))
                .andExpect(jsonPath("$.head_to_head[0].wins").value(1))
                .andExpect(jsonPath("$.last_results[0].fight_id").value(result.fightId()))
                .andExpect(jsonPath("$.last_results[0].result").value("won"))
                .andExpect(jsonPath("$.last_results[0].ended_by").value("ko"));
    }

    @Test
    void anUnknownGladiatorIsA404() throws Exception {
        mockMvc.perform(get("/gladiators/nobody").accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
        mockMvc.perform(get("/gladiators/nobody").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
    }

    @Test
    void thePagesLinkGladiatorsAndTheirFights() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        AnnouncedFight fight = fightService.current().announced();
        FightResult result = game.finishFight();

        String roster = html("/gladiators");
        assertTrue(roster.contains("href=\"/gladiators/" + fight.red().slug() + "\""), roster);

        String page = html("/gladiators/" + fight.red().slug());
        assertTrue(page.contains("Win rate"), page);
        assertTrue(page.contains("100.0%"), page); // win rate and KO rate
        assertTrue(page.contains("href=\"/fights/" + result.fightId() + "\""), page);
        assertTrue(page.contains("href=\"/gladiators/" + fight.blue().slug() + "\""), page); // head to head
    }

    @Test
    void theBoardsCornerCardsLinkToTheGladiators() throws Exception {
        LiveFight live = game.startFight();
        AnnouncedFight next = fightService.current().announced();

        String board = html("/fragments/board");
        for (String slug : new String[] {live.red().slug(), live.blue().slug(), next.red().slug(), next.blue().slug()}) {
            assertTrue(board.contains("href=\"/gladiators/" + slug + "\""), slug + " in " + board);
        }
    }

    private String html(String path) throws Exception {
        return mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
