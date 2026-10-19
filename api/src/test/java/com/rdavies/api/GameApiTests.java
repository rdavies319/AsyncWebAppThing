package com.rdavies.api;

import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundEngine;
import com.rdavies.api.round.RoundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The JSON API from spec section 8, through HTTP. Game rules themselves are covered by GameFlowTests. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GameFlowTests.ClockConfig.class})
@Transactional
class GameApiTests {
    private static final Instant ROUND_START = Instant.parse("2026-01-01T00:00:00.200Z");

    @Autowired RoundEngine engine;
    @Autowired RoundRepository roundRepository;
    @Autowired GameFlowTests.MutableClock clock;
    @Autowired MockMvc mockMvc;

    @Test
    void currentRoundIs404BeforeTheFirstRound() throws Exception {
        mockMvc.perform(get("/rounds/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void currentRoundShowsTheRollOddsAndCommitment() throws Exception {
        Round round = openRound();
        Round next = roundRepository.findScheduledRound().orElseThrow();
        mockMvc.perform(get("/rounds/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round_id").value(round.id()))
                .andExpect(jsonPath("$.roll").value(round.rollValue()))
                .andExpect(jsonPath("$.starts_at").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.cutoff_at").value("2026-01-01T00:00:50Z"))
                .andExpect(jsonPath("$.server_time").value("2026-01-01T00:00:00.200Z"))
                .andExpect(jsonPath("$.next_seed_commit_hash").value(next.seedCommitHash()))
                .andExpect(jsonPath("$.higher.multiplier_bps").exists());
    }

    @Test
    void placingAPredictionReturnsItWithTheLockedInMultiplier() throws Exception {
        Round round = openRound();
        String direction = round.rollValue() < 100 ? "higher" : "lower";
        predict(round.id(), direction, 100)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.round_id").value(round.id()))
                .andExpect(jsonPath("$.direction").value(direction))
                .andExpect(jsonPath("$.stake").value(100))
                .andExpect(jsonPath("$.multiplier_bps").isNumber())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.payout").value(nullValue()));
    }

    @Test
    void directionIsCaseInsensitive() throws Exception {
        Round round = openRound();
        predict(round.id(), round.rollValue() < 100 ? "HIGHER" : "Lower", 10).andExpect(status().isCreated());
    }

    @Test
    void ruleViolationsAre400() throws Exception {
        Round round = openRound();
        predict(round.id(), round.rollValue() < 100 ? "higher" : "lower", 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Stake must be between 10 and")));
    }

    @Test
    void malformedRequestsAre400() throws Exception {
        Round round = openRound();
        predict(round.id(), "sideways", 10).andExpect(status().isBadRequest());
        mockMvc.perform(post("/predictions").contentType(MediaType.APPLICATION_JSON).content("{\"stake\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a round"));
    }

    @Test
    void aSecondPredictionIs409() throws Exception {
        Round round = openRound();
        String direction = round.rollValue() < 100 ? "higher" : "lower";
        predict(round.id(), direction, 10).andExpect(status().isCreated());
        predict(round.id(), direction, 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have already predicted this round"));
    }

    @Test
    void predictingAfterTheCutoffIs409() throws Exception {
        Round round = openRound();
        clock.now = Instant.parse("2026-01-01T00:00:50Z");
        predict(round.id(), round.rollValue() < 100 ? "higher" : "lower", 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Predictions for this round have closed"));
    }

    private Round openRound() {
        clock.now = ROUND_START;
        engine.tick();
        return roundRepository.findOpenRound().orElseThrow();
    }

    private ResultActions predict(long roundId, String direction, long stake) throws Exception {
        return mockMvc.perform(post("/predictions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"round_id\": %d, \"direction\": \"%s\", \"stake\": %d}".formatted(roundId, direction, stake)));
    }
}
