package com.rdavies.api;

import com.rdavies.api.round.Round;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.TestWallets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.ServletException;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The JSON API from spec section 8, through HTTP. Game rules themselves are covered by GameFlowTests. */
@GameTest
@Transactional
class GameApiTests {
    @Autowired TestGame game;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;
    private GamePrincipal player;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @BeforeEach
    void signUp() {
        player = TestPlayers.signUp(userService, "player");
    }

    @Test
    void currentRoundIs404BeforeTheFirstRound() throws Exception {
        mockMvc.perform(get("/rounds/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void currentRoundShowsTheRollOddsAndCommitment() throws Exception {
        Round round = openRound();
        Round next = game.scheduled();
        mockMvc.perform(get("/rounds/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round_id").value(round.id()))
                .andExpect(jsonPath("$.roll").value(round.rollValue()))
                .andExpect(jsonPath("$.starts_at").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.cutoff_at").value("2026-01-01T00:00:50Z"))
                .andExpect(jsonPath("$.server_time").value("2026-01-01T00:00:00.200Z"))
                .andExpect(jsonPath("$.next_seed_commit_hash").value(next.seedCommitHash()))
                .andExpect(jsonPath("$.higher.multiplier_bps").exists())
                .andExpect(jsonPath("$.higher.multiplier").doesNotExist()) // display helpers stay out of the API
                .andExpect(jsonPath("$.roll_at").doesNotExist());
    }

    @Test
    void placingAPredictionReturnsItWithTheLockedInMultiplier() throws Exception {
        Round round = openRound();
        String direction = allowedDirection(round);
        predict(round.id(), direction, 100)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.round_id").value(round.id()))
                .andExpect(jsonPath("$.direction").value(direction))
                .andExpect(jsonPath("$.stake").value(100))
                .andExpect(jsonPath("$.multiplier_bps").isNumber())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.payout").value(nullValue()))
                .andExpect(jsonPath("$.multiplier").doesNotExist());
    }

    @Test
    void directionIsCaseInsensitive() throws Exception {
        Round round = openRound();
        predict(round.id(), round.rollValue() < 100 ? "HIGHER" : "Lower", 10).andExpect(status().isCreated());
    }

    @Test
    void ruleViolationsAre400() throws Exception {
        Round round = openRound();
        predict(round.id(), allowedDirection(round), 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Stake must be between 10 and")));
    }

    @Test
    void theCurrentRoundsOddsFollowItsRoll() throws Exception {
        Round round = game.startFrom(99); // HIGHER pays 98x, so the 10,000 payout cap limits its stake to 102
        mockMvc.perform(get("/rounds/current"))
                .andExpect(jsonPath("$.roll").value(99))
                .andExpect(jsonPath("$.higher.multiplier_bps").value(980000))
                .andExpect(jsonPath("$.higher.max_stake").value(102))
                .andExpect(jsonPath("$.lower.multiplier_bps").value(10000));
        predict(round.id(), "higher", 103)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Stake must be between 10 and 102 pts on HIGHER"));
        predict(round.id(), "higher", 102).andExpect(status().isCreated());
    }

    @Test
    void malformedRequestsAre400() throws Exception {
        Round round = openRound();
        predict(round.id(), "sideways", 10).andExpect(status().isBadRequest());
        mockMvc.perform(post("/predictions").with(user(player)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"stake\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a round"));
    }

    @Test
    void aSecondPredictionIs409() throws Exception {
        Round round = openRound();
        String direction = allowedDirection(round);
        predict(round.id(), direction, 10).andExpect(status().isCreated());
        predict(round.id(), direction, 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have already predicted this round"));
    }

    @Test
    void predictingAfterTheCutoffIs409() throws Exception {
        Round round = openRound();
        clock.now = Instant.parse("2026-01-01T00:00:50Z");
        predict(round.id(), allowedDirection(round), 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Predictions for this round have closed"));
    }

    @Test
    void internalFaultsAreNotReportedAsRuleViolations() {
        // A logged-in player with no wallet: a bug, so no 409 ProblemDetail with the internal message
        GamePrincipal ghost = new GamePrincipal(Long.MAX_VALUE, "ghost@example.invalid", "ghost", "!");
        ServletException e = assertThrows(ServletException.class,
                () -> mockMvc.perform(post("/wallet/topup").with(user(ghost)).with(csrf())));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    private Round openRound() {
        return game.open();
    }

    private static String allowedDirection(Round round) {
        return TestGame.allowedDirection(round).name().toLowerCase();
    }

    private ResultActions predict(long roundId, String direction, long stake) throws Exception {
        return mockMvc.perform(post("/predictions").with(user(player)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"round_id\": %d, \"direction\": \"%s\", \"stake\": %d}".formatted(roundId, direction, stake)));
    }
}
