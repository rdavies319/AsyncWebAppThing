package com.rdavies.arena;

import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.fight.records.RoundView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /fights and GET /fights/{id} (spec section 9): public fight history, as JSON and as pages. */
@GameTest
@Transactional
class FightPagesTests {
    /** Field names that would leak a stat, the seed or a raw roll. */
    static final List<String> INTERNAL = List.of(
            "\"hp\"", "attack_min", "attack_max", "defence", "agility", "seed", "_roll");

    @Autowired TestGame game;
    @Autowired FightService fightService;
    @Autowired MockMvc mockMvc;

    @Test
    void theHistoryListsSettledFightsNewestFirst() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        FightResult first = game.finishFight();
        game.plantFight(Winner.DRAW, EndedBy.DOUBLE_KO);
        FightResult second = game.finishFight(); // the 03:00 fight is now announced

        mockMvc.perform(get("/fights").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fight_id").value(second.fightId()))
                .andExpect(jsonPath("$[0].starts_at").value("2026-01-01T02:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("settled"))
                .andExpect(jsonPath("$[0].winner").value("draw"))
                .andExpect(jsonPath("$[0].ended_by").value("double_ko"))
                .andExpect(jsonPath("$[1].fight_id").value(first.fightId()))
                .andExpect(jsonPath("$[1].winner").value("red"))
                .andExpect(jsonPath("$[1].rounds_done").value(first.roundsDone()))
                .andExpect(jsonPath("$[1].red.elo").value(1000))
                .andExpect(jsonPath("$[1].red.multiplier_bps").value(19600));

        mockMvc.perform(get("/fights?limit=1").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fight_id").value(second.fightId()));
    }

    @Test
    void aFightShowsItsMatchupOddsResultAndRoundLog() throws Exception {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        LiveFight live = game.startFight();
        FightResult result = game.finishFight();
        List<RoundView> rounds = fightService.rounds(result.fightId());

        mockMvc.perform(get("/fights/" + result.fightId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fight.fight_id").value(result.fightId()))
                .andExpect(jsonPath("$.fight.red.name").value(live.red().name()))
                .andExpect(jsonPath("$.fight.blue.slug").value(live.blue().slug()))
                .andExpect(jsonPath("$.fight.blue.elo").value(1000))
                .andExpect(jsonPath("$.fight.blue.multiplier_bps").value(19600))
                .andExpect(jsonPath("$.fight.winner").value("blue"))
                .andExpect(jsonPath("$.fight.ended_by").value("ko"))
                .andExpect(jsonPath("$.rounds", hasSize(result.roundsDone())))
                .andExpect(jsonPath("$.rounds[0].n").value(1))
                .andExpect(jsonPath("$.rounds[0].red_damage").value(rounds.getFirst().redDamage()))
                .andExpect(jsonPath("$.rounds[0].blue_hp_after").value(rounds.getFirst().blueHpAfter()));
    }

    @Test
    void aLiveFightShowsOnlyWhatHasHappenedSoFar() throws Exception {
        LiveFight live = game.startFight();
        game.nextRound();

        mockMvc.perform(get("/fights/" + live.fightId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fight.status").value("live"))
                .andExpect(jsonPath("$.fight.winner").value(nullValue()))
                .andExpect(jsonPath("$.rounds", hasSize(1)));

        long announced = fightService.current().announced().fightId();
        mockMvc.perform(get("/fights/" + announced).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fight.status").value("announced"))
                .andExpect(jsonPath("$.rounds", hasSize(0)));
    }

    @Test
    void anUnknownFightIsA404() throws Exception {
        mockMvc.perform(get("/fights/987654").accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
        mockMvc.perform(get("/fights/987654").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
    }

    @Test
    void thePagesLinkEachFightAndReadTheRoundLog() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        LiveFight live = game.startFight();
        FightResult result = game.finishFight();
        RoundView first = fightService.rounds(result.fightId()).getFirst();

        String list = html("/fights");
        assertTrue(list.contains("href=\"/fights/" + result.fightId() + "\""), list);
        assertTrue(list.contains(live.red().name() + " beat " + live.blue().name() + " by KO"), list);

        String page = html("/fights/" + result.fightId());
        assertTrue(page.contains(live.red().name() + " beat " + live.blue().name() + " by KO"), page);
        assertTrue(page.contains("1.96x"), page);
        String redAttack = first.redHit()
                ? live.red().name() + " hits " + live.blue().name() + " for " + first.redDamage()
                : live.blue().name() + " dodges";
        assertTrue(page.contains("1. " + redAttack), page);
        assertTrue(page.contains(live.red().name() + " " + Math.max(0, first.redHpAfter()) + " HP, "
                + live.blue().name() + " " + Math.max(0, first.blueHpAfter()) + " HP"), page); // HP after the round
    }

    @Test
    void theBoardsLastResultLinksToItsFight() throws Exception {
        FightResult result = game.finishFight();
        String board = html("/fragments/board");
        assertTrue(board.contains("href=\"/fights/" + result.fightId() + "\""), board);
    }

    @Test
    void nothingInternalIsShown() throws Exception {
        game.plantFight(Winner.RED, EndedBy.KO);
        FightResult result = game.finishFight();

        for (String body : List.of(
                json("/fights"), json("/fights/" + result.fightId()), html("/fights"), html("/fights/" + result.fightId()))) {
            for (String field : INTERNAL) {
                assertFalse(body.contains(field), field + " in " + body);
            }
        }
    }

    private String json(String path) throws Exception {
        return mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String html(String path) throws Exception {
        return mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
