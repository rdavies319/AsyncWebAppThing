package com.rdavies.api;

import com.rdavies.api.domain.PredictionType;
import com.rdavies.api.domain.Round;
import com.rdavies.api.domain.RoundStatus;
import com.rdavies.api.repository.RoundRepository;
import com.rdavies.api.repository.WalletRepository;
import com.rdavies.api.service.GameService;
import com.rdavies.api.service.ProvablyFair;
import com.rdavies.api.service.RoundEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GameFlowTests {
    private static final long DEMO_USER = 1L;
    private static final Instant ROUND_START = Instant.parse("2026-01-01T00:00:00.200Z");

    @Autowired RoundEngine engine;
    @Autowired GameService gameService;
    @Autowired RoundRepository roundRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void openRound() {
        clock.now = ROUND_START;
        engine.tick();
    }

    @Test
    void migrationsSeedTheDemoWallet() {
        assertEquals(1000, balance());
        assertLedgerMatchesWallet();
    }

    @Test
    void gamePageRendersTheCurrentRound() throws Exception {
        Round round = currentRound();
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"" + round.roundId() + "\"")));
    }

    @Test
    void gamePageShowsTheLastRollAndYourBet() throws Exception {
        Round round = currentRound();
        gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 10L);
        mockMvc.perform(get("/"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your bet:")));

        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();

        Round settled = roundRepository.findById(round.roundId()).orElseThrow();
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>" + settled.currentRoll() + "</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Your bet:"))));
    }

    @Test
    void settlingARoundKeepsTheLedgerBalanced() {
        Round round = currentRound();
        gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 100L);
        assertEquals(900, balance());

        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();

        Round settled = roundRepository.findById(round.roundId()).orElseThrow();
        assertEquals(RoundStatus.SETTLED, settled.status());
        assertNotNull(settled.currentRoll());
        assertEquals(RoundStatus.OPEN, currentRound().status());
        assertEquals(settled.currentRoll(), currentRound().previousRoll());
        assertLedgerMatchesWallet();
    }

    @Test
    void negativeAndZeroStakesAreRejected() {
        Round round = currentRound();
        assertThrows(IllegalArgumentException.class,
                () -> gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), -1000L));
        assertThrows(IllegalArgumentException.class,
                () -> gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 0L));
        assertEquals(1000, balance());
    }

    @Test
    void predictionsCloseFiveSecondsBeforeTheRoll() {
        Round round = currentRound();
        clock.now = Instant.parse("2026-01-01T00:00:55Z");
        assertThrows(IllegalStateException.class,
                () -> gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 10L));
        assertEquals(1000, balance());
    }

    @Test
    void secondPredictionInARoundIsRejected() {
        Round round = currentRound();
        gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 10L);
        assertThrows(IllegalStateException.class,
                () -> gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 10L));
        assertEquals(990, balance());
    }

    @Test
    void repeatTicksNeverRollOrPayTwice() {
        Round round = currentRound();
        gameService.placePrediction(DEMO_USER, round.roundId(), allowedDirection(round), 100L);

        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();
        long balanceAfterSettle = balance();
        int ledgerRowsAfterSettle = ledgerRows();
        Round next = currentRound();

        engine.tick();
        clock.now = ROUND_START.plusSeconds(75);
        engine.tick();

        assertEquals(balanceAfterSettle, balance());
        assertEquals(ledgerRowsAfterSettle, ledgerRows());
        assertEquals(next.roundId(), currentRound().roundId());
        assertEquals(2, roundRepository.count());
        assertLedgerMatchesWallet();
    }

    @Test
    void roundsOpenOnTheMinuteAndRollAtTheNext() {
        Round first = currentRound();
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.startTime());

        // A slightly late tick still settles, and the next round keeps the minute slot
        clock.now = ROUND_START.plusSeconds(70);
        engine.tick();
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(first.roundId()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), currentRound().startTime());
    }

    @Test
    void roundMissedDuringDowntimeIsVoidedAndRefunded() {
        Round stale = currentRound();
        gameService.placePrediction(DEMO_USER, stale.roundId(), allowedDirection(stale), 100L);

        clock.now = Instant.parse("2026-01-01T00:10:20Z");
        engine.tick();

        Round voided = roundRepository.findById(stale.roundId()).orElseThrow();
        assertEquals(RoundStatus.VOIDED, voided.status());
        assertNull(voided.currentRoll());
        assertEquals(stale.seed(), voided.revealedSeed());
        assertEquals(1000, balance());
        assertLedgerMatchesWallet();

        Round fresh = currentRound();
        assertEquals(Instant.parse("2026-01-01T00:10:00Z"), fresh.startTime());
        assertEquals(stale.previousRoll(), fresh.previousRoll());
        assertEquals(2, roundRepository.count());

        // Catching up again changes nothing
        engine.tick();
        assertEquals(fresh.roundId(), currentRound().roundId());
        assertEquals(1000, balance());
    }

    @Test
    void rollIsCommittedBeforeAndVerifiableAfter() {
        Round open = currentRound();
        assertNotNull(open.seedCommitHash());
        assertNull(open.revealedSeed());

        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();

        Round settled = roundRepository.findById(open.roundId()).orElseThrow();
        String seed = settled.revealedSeed();
        assertNotNull(seed);
        assertEquals(open.seedCommitHash(), ProvablyFair.commit(seed));
        assertEquals(ProvablyFair.roll(seed), settled.currentRoll());
    }

    private int ledgerRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger WHERE user_id = ?", Integer.class, DEMO_USER);
    }

    private Round currentRound() {
        return gameService.getCurrentRound().orElseThrow();
    }

    private static PredictionType allowedDirection(Round round) {
        return round.previousRoll() < 100 ? PredictionType.HIGHER : PredictionType.LOWER;
    }

    private long balance() {
        return walletRepository.findById(DEMO_USER).orElseThrow().balance();
    }

    private void assertLedgerMatchesWallet() {
        Long ledgerTotal = jdbc.queryForObject("SELECT SUM(amount) FROM ledger WHERE user_id = ?", Long.class, DEMO_USER);
        assertEquals(balance(), ledgerTotal);
    }

    static class MutableClock extends Clock {
        Instant now = Instant.EPOCH;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }
}
