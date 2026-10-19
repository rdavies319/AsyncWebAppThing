package com.rdavies.api;

import com.rdavies.api.live.LiveStreams;
import com.rdavies.api.prediction.Prediction;
import com.rdavies.api.prediction.PredictionRepository;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionSettled;
import com.rdavies.api.prediction.PredictionStatus;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.ProvablyFair;
import com.rdavies.api.round.Round;
import com.rdavies.api.round.RoundEngine;
import com.rdavies.api.round.RoundOpened;
import com.rdavies.api.round.RoundRepository;
import com.rdavies.api.round.RoundSettled;
import com.rdavies.api.round.RoundStatus;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.rdavies.api.TestGame.ROUND_START;
import static com.rdavies.api.TestGame.allowedDirection;
import static com.rdavies.api.TestGame.assertLedgerMatchesWallet;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@GameTest
@Transactional
@RecordApplicationEvents
class GameFlowTests {
    private GamePrincipal player;
    private long playerId;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired RoundEngine engine;
    @Autowired UserService userService;
    @Autowired PredictionService predictionService;
    @Autowired RoundRepository roundRepository;
    @Autowired PredictionRepository predictionRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationEvents events;
    @Autowired LiveStreams liveStreams;

    @BeforeEach
    void openRound() {
        player = TestPlayers.signUp(userService, "player");
        playerId = player.id();
        TestGame.openRound(engine, clock, roundRepository);
    }

    @Test
    void bootstrapOpensARolledRoundWithTheNextOneScheduled() {
        Round first = currentRound();
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.startsAt());
        assertEquals(ProvablyFair.roll(first.seed(), first.id()), first.rollValue());

        Round next = scheduledRound();
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), next.startsAt());
        assertEquals(ProvablyFair.commit(next.seed()), next.seedCommitHash());
        assertNull(next.revealedSeed());
        assertNull(next.rollValue());
        assertEquals(2, roundRepository.count());
    }

    @Test
    void liveStreamStartsWithAServerTimeAndRoundSnapshot() throws Exception {
        startFrom(99);
        Round round = currentRound();
        MvcResult result = mockMvc.perform(get("/live")).andExpect(request().asyncStarted()).andReturn();
        String body = result.getResponse().getContentAsString();

        assertTrue(body.startsWith("event:server_time\ndata:{\"server_time\":\"2026-01-01T00:01:00.200Z\"}"), body);
        assertTrue(body.contains("event:round_open\ndata:{\"round_id\":" + round.id() + ",\"roll\":99,"), body);
        assertTrue(body.contains("\"cutoff_at\":\"2026-01-01T00:01:50Z\""), body);
        // R = 99: Higher pays 98x with its stake capped at 102, Lower pays 0.98 / 0.98 = 1x
        assertTrue(body.contains("\"higher\":{\"multiplier_bps\":980000,\"max_stake\":102}"), body);
        assertTrue(body.contains("\"lower\":{\"multiplier_bps\":10000,\"max_stake\":500}"), body);
        assertTrue(body.contains("\"next_seed_commit_hash\":\"" + scheduledRound().seedCommitHash() + "\""), body);

        // Later events are sent as JSON serialised once, and reach the stream as is
        liveStreams.broadcast("round_settled", "{\"round_id\":1}");
        body = result.getResponse().getContentAsString();
        assertTrue(body.endsWith("event:round_settled\ndata:{\"round_id\":1}\n\n"), body);
    }

    @Test
    void settlingPublishesTheRollAndEachPlayersResult() {
        Instant start = startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, currentRound().id(), PredictionType.HIGHER, 100L);
        Round next = scheduledRound();
        events.clear();

        plantNextRoll(80);
        settleRoundStartedAt(start);

        Round rolled = roundRepository.findById(next.id()).orElseThrow();
        assertEquals(List.of(new RoundSettled(placed.roundId(), next.id(), 80, rolled.seed())),
                events.stream(RoundSettled.class).toList());
        assertEquals(List.of(new PredictionSettled(playerId, placed.id(), placed.roundId(), PredictionStatus.WON, 196, 1096)),
                events.stream(PredictionSettled.Batch.class).flatMap(b -> b.predictions().stream()).toList());
        RoundView opened = events.stream(RoundOpened.class).map(RoundOpened::view).findFirst().orElseThrow();
        assertEquals(1, events.stream(RoundOpened.class).count());
        assertEquals(next.id(), opened.roundId());
        assertEquals(80, opened.roll());
        assertEquals(scheduledRound().seedCommitHash(), opened.nextSeedCommitHash());
    }

    @Test
    void gamePageShowsEachSidesOddsAndListensForNewRounds() throws Exception {
        startFrom(50);
        String page = mockMvc.perform(get("/").with(user(player))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("sse-connect=\"/live\""), page);
        assertTrue(page.contains("hx-trigger=\"sse:round_open\""), page);
        assertTrue(page.contains("1.96x · max 500"), page);
        assertTrue(page.contains("2.00x · max 500"), page);
    }

    @Test
    void anImpossibleSideIsDisabled() throws Exception {
        startFrom(100);
        String page = mockMvc.perform(get("/").with(user(player))).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("value=\"HIGHER\" class=\"side\" disabled=\"disabled\""), page);
        assertFalse(page.contains("value=\"LOWER\" class=\"side\" disabled"), page);
    }

    @Test
    void boardFragmentIsJustTheBoard() throws Exception {
        String board = mockMvc.perform(get("/fragments/board")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.strip().startsWith("<div id=\"board\""), board);
        assertFalse(board.contains("<html"), board);
    }

    @Test
    void predictingFromThePageReturnsTheBoardWithYourBet() throws Exception {
        startFrom(50);
        String board = mockMvc.perform(post("/predict").with(user(player)).with(csrf())
                        .param("roundId", currentRound().id().toString())
                        .param("prediction", "LOWER").param("stake", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.contains("Your bet:"), board);
        assertTrue(board.contains("at <span>2.00x</span>"), board);
        assertTrue(board.contains("<span>900</span> pts"), board);
    }

    @Test
    void aRejectedPredictionShowsTheErrorOnTheBoard() throws Exception {
        startFrom(50);
        String board = mockMvc.perform(post("/predict").with(user(player)).with(csrf())
                        .param("roundId", currentRound().id().toString())
                        .param("prediction", "HIGHER").param("stake", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.contains("Stake must be between 10 and 500 pts on HIGHER"), board);
        assertTrue(board.contains("value=\"5\""), board); // keeps what the player typed
        assertEquals(1000, balance());
    }

    @Test
    void gamePageRendersTheCurrentRound() throws Exception {
        Round round = currentRound();
        mockMvc.perform(get("/").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"" + round.id() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        scheduledRound().seedCommitHash())));
    }

    @Test
    void gamePageShowsTheLastRollAndYourBet() throws Exception {
        Round round = currentRound();
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L);
        mockMvc.perform(get("/").with(user(player)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your bet:")));

        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();

        mockMvc.perform(get("/").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>" + currentRound().rollValue() + "</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Your bet:"))));
    }

    @Test
    void aWinPaysTheMultiplierLockedInWhenPlaced() {
        Instant start = startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, currentRound().id(), PredictionType.HIGHER, 100L);
        assertEquals(19_600, placed.multiplierBps());
        assertEquals(900, balance());

        plantNextRoll(80);
        settleRoundStartedAt(start);

        Prediction settled = predictionRepository.findById(placed.id()).orElseThrow();
        assertEquals(PredictionStatus.WON, settled.status());
        assertEquals(196, settled.payout());
        assertEquals(1096, balance());
        assertEquals(196, jdbc.queryForObject(
                "SELECT delta FROM ledger WHERE reason = 'PAYOUT' AND ref_id = ?", Long.class, placed.id()));
        assertLedgerMatchesWallet(jdbc, playerId);
    }

    @Test
    void aLossKeepsTheStake() {
        Instant start = startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, currentRound().id(), PredictionType.HIGHER, 100L);

        plantNextRoll(20);
        settleRoundStartedAt(start);

        Prediction settled = predictionRepository.findById(placed.id()).orElseThrow();
        assertEquals(PredictionStatus.LOST, settled.status());
        assertEquals(0, settled.payout());
        assertEquals(900, balance());
        assertEquals(2, ledgerRows()); // signup bonus and stake only
        assertLedgerMatchesWallet(jdbc, playerId);
    }

    @Test
    void aTieRefundsTheStake() {
        Instant start = startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, currentRound().id(), PredictionType.LOWER, 100L);

        plantNextRoll(50);
        settleRoundStartedAt(start);

        Prediction settled = predictionRepository.findById(placed.id()).orElseThrow();
        assertEquals(PredictionStatus.PUSH, settled.status());
        assertEquals(100, settled.payout());
        assertEquals(1000, balance());
        assertEquals(100, jdbc.queryForObject(
                "SELECT delta FROM ledger WHERE reason = 'REFUND' AND ref_id = ?", Long.class, placed.id()));
        assertLedgerMatchesWallet(jdbc, playerId);
    }

    @Test
    void impossibleDirectionsAreRejected() {
        startFrom(100);
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, currentRound().id(), PredictionType.HIGHER, 10L));
        assertEquals(1000, balance());
    }

    @Test
    void negativeAndZeroStakesAreRejected() {
        Round round = currentRound();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), -1000L));
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 0L));
        assertEquals(1000, balance());
    }

    @Test
    void predictionsCloseTenSecondsBeforeTheRoll() {
        Round round = currentRound();
        clock.now = Instant.parse("2026-01-01T00:00:50Z");
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L));
        assertEquals(1000, balance());

        clock.now = Instant.parse("2026-01-01T00:00:49.999Z");
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L);
        assertEquals(990, balance());
    }

    @Test
    void stakesBelowTheMinimumAreRejected() {
        Round round = currentRound();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 9L));
        assertEquals(1000, balance());
    }

    @Test
    void stakesAboveTheMaximumAreRejected() {
        startFrom(50);
        long roundId = currentRound().id();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 501L));
        predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 500L);
        assertEquals(500, balance());
    }

    @Test
    void thePayoutCapLowersTheMaxStakeOnLongShots() {
        startFrom(99); // HIGHER pays 98x, so the 10,000 cap allows at most 102
        long roundId = currentRound().id();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 103L));
        predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 102L);
        assertEquals(898, balance());
    }

    @Test
    void insufficientPointsAreRejected() {
        jdbc.update("UPDATE wallets SET balance = 50 WHERE user_id = ?", playerId);
        Round round = currentRound();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 100L));
        assertEquals(50, balance());
        assertEquals(1, ledgerRows()); // the signup bonus only: no STAKE row
        // The prediction row was saved before the debit failed. In production the throw rolls it back, but this
        // test runs inside its own transaction, which the failed call only marks rollback-only.
    }

    @Test
    void settlementPaysTheStoredMultiplierNotTheCurrentOdds() {
        Instant start = startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, currentRound().id(), PredictionType.HIGHER, 100L);
        // Stands in for odds that changed after the prediction was placed
        jdbc.update("UPDATE predictions SET multiplier_bps = 30000 WHERE id = ?", placed.id());

        plantNextRoll(80);
        settleRoundStartedAt(start);

        assertEquals(300, predictionRepository.findById(placed.id()).orElseThrow().payout());
    }

    @Test
    void secondPredictionInARoundIsRejected() {
        Round round = currentRound();
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L);
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L));
        assertEquals(990, balance());
    }

    @Test
    void repeatTicksNeverRollOrPayTwice() {
        Round round = currentRound();
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 100L);

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
        assertEquals(next.id(), currentRound().id());
        assertEquals(3, roundRepository.count()); // settled, open, scheduled
        assertLedgerMatchesWallet(jdbc, playerId);
    }

    @Test
    void roundsOpenOnTheMinuteAndRollAtTheNext() {
        Round first = currentRound();
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.startsAt());

        // A slightly late tick still settles, and the next round keeps its minute slot
        clock.now = ROUND_START.plusSeconds(70);
        engine.tick();
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(first.id()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), currentRound().startsAt());
        assertEquals(Instant.parse("2026-01-01T00:02:00Z"), scheduledRound().startsAt());
    }

    @Test
    void roundMissedDuringDowntimeIsSettledWithItsCommittedSeed() {
        Round stale = currentRound();
        Round committed = scheduledRound();
        Prediction placed = predictionService.placePrediction(playerId, stale.id(), allowedDirection(stale), 100L);

        clock.now = Instant.parse("2026-01-01T00:10:20Z");
    @Test
    void aTickFiringJustBeforeTheMinuteCountsAsOnIt() {
        Round first = currentRound();

        // Well before the minute, nothing is due
        clock.now = Instant.parse("2026-01-01T00:00:58Z");
        engine.tick();
        assertEquals(first.id(), currentRound().id());

        // The scheduler fires a few ms early: the roll still happens, and the next round takes the
        // coming minute rather than the one ending, so it gets its full betting window
        clock.now = Instant.parse("2026-01-01T00:00:59.995Z");
        engine.tick();
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(first.id()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), currentRound().startsAt());

        // And the next early tick, a minute on, rolls that one
        Round second = currentRound();
        clock.now = Instant.parse("2026-01-01T00:01:59.995Z");
        engine.tick();
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(second.id()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:02:00Z"), currentRound().startsAt());
    }

        engine.tick();

        assertEquals(RoundStatus.SETTLED, roundRepository.findById(stale.id()).orElseThrow().status());
        assertNotEquals(PredictionStatus.PENDING, predictionRepository.findById(placed.id()).orElseThrow().status());
        assertLedgerMatchesWallet(jdbc, playerId);

        // The committed round opens on the current minute with the roll its seed always implied
        Round reopened = currentRound();
        assertEquals(committed.id(), reopened.id());
        assertEquals(Instant.parse("2026-01-01T00:10:00Z"), reopened.startsAt());
        assertEquals(ProvablyFair.roll(committed.seed(), committed.id()), reopened.rollValue());
        assertEquals(Instant.parse("2026-01-01T00:11:00Z"), scheduledRound().startsAt());
        assertEquals(3, roundRepository.count()); // no rounds for the missed minutes

        // Catching up again changes nothing
        long balance = balance();
        engine.tick();
        assertEquals(reopened.id(), currentRound().id());
        assertEquals(balance, balance());
    }

    @Test
    void rollIsCommittedBeforeAndVerifiableAfter() {
        Round next = scheduledRound();
        assertNotNull(next.seedCommitHash());
        assertNull(next.revealedSeed());

        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();

        Round rolled = roundRepository.findById(next.id()).orElseThrow();
        String seed = rolled.revealedSeed();
        assertNotNull(seed);
        assertEquals(next.seedCommitHash(), ProvablyFair.commit(seed));
        assertEquals(ProvablyFair.roll(seed, rolled.id()), rolled.rollValue());
    }

    @Test
    void rollsCannotBeRewritten() {
        Round round = currentRound();
        int otherRoll = round.rollValue() == 1 ? 2 : 1;
        DataAccessException e = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE rounds SET roll_value = ? WHERE id = ?", otherRoll, round.id()));
        assertTrue(e.getMostSpecificCause().getMessage().contains("immutable"), e.getMessage());
    }

    @Test
    void ledgerIsAppendOnly() {
        DataAccessException e = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM ledger WHERE user_id = ?", playerId));
        assertTrue(e.getMostSpecificCause().getMessage().contains("append-only"), e.getMessage());
    }

    @Test
    void settlesAThousandPredictionsInUnderTwoSeconds() {
        Instant start = startFrom(50);
        long roundId = currentRound().id();
        // 1,000 players who each staked 100 of their 1,000 points: half HIGHER, half LOWER
        jdbc.update("""
                INSERT INTO users (email, display_name, password_hash)
                SELECT 'load' || g || '@example.invalid', 'load' || g, '!' FROM generate_series(1, 1000) g""");
        jdbc.update("INSERT INTO wallets (user_id, balance) SELECT id, 900 FROM users WHERE email LIKE 'load%'");
        jdbc.update("""
                INSERT INTO ledger (user_id, delta, reason, created_at)
                SELECT id, 1000, 'SIGNUP_BONUS', now() FROM users WHERE email LIKE 'load%'""");
        jdbc.update("""
                INSERT INTO predictions (user_id, round_id, direction, stake, multiplier_bps, status, created_at)
                SELECT id, ?, CASE WHEN id % 2 = 0 THEN 'HIGHER' ELSE 'LOWER' END, 100, 19600, 'PENDING', now()
                FROM users WHERE email LIKE 'load%'""", roundId);
        jdbc.update("""
                INSERT INTO ledger (user_id, delta, reason, ref_id, created_at)
                SELECT user_id, -100, 'STAKE', id, now() FROM predictions WHERE round_id = ?""", roundId);

        plantNextRoll(70);
        long began = System.nanoTime();
        settleRoundStartedAt(start);
        long millis = (System.nanoTime() - began) / 1_000_000;

        assertTrue(millis < 2_000, "settlement took " + millis + " ms");
        assertEquals(500, jdbc.queryForObject(
                "SELECT COUNT(*) FROM predictions WHERE round_id = ? AND status = 'WON'", Integer.class, roundId));
        assertEquals(500, jdbc.queryForObject(
                "SELECT COUNT(*) FROM predictions WHERE round_id = ? AND status = 'LOST'", Integer.class, roundId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM wallets w
                WHERE w.balance <> (SELECT COALESCE(SUM(delta), 0) FROM ledger l WHERE l.user_id = w.user_id)""",
                Integer.class));
    }

    /**
     * Settles the bootstrap round against {@code roll}, so the open round shows that number.
     * Returns the open round's start, at 00:01:00.
     */
    private Instant startFrom(int roll) {
        plantNextRoll(roll);
        clock.now = ROUND_START.plusSeconds(60);
        engine.tick();
        assertEquals(roll, currentRound().rollValue());
        return currentRound().startsAt();
    }

    private void settleRoundStartedAt(Instant start) {
        clock.now = start.plusSeconds(60);
        engine.tick();
    }

    /**
     * Replaces the scheduled round with one whose seed rolls {@code wanted}, found by trying seeds
     * (about 100 tries). Stands in for chance; the engine still derives the roll from the seed.
     */
    private void plantNextRoll(int wanted) {
        Round next = scheduledRound();
        String seed;
        do {
            seed = ProvablyFair.newSeed(RANDOM);
        } while (ProvablyFair.roll(seed, next.id()) != wanted);
        jdbc.update("DELETE FROM rounds WHERE id = ?", next.id());
        jdbc.update("INSERT INTO rounds (id, starts_at, seed_commit_hash, seed, status) VALUES (?, ?, ?, ?, 'SCHEDULED')",
                next.id(), Timestamp.from(next.startsAt()), ProvablyFair.commit(seed), seed);
    }

    private int ledgerRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger WHERE user_id = ?", Integer.class, playerId);
    }

    private Round currentRound() {
        return roundRepository.findOpenRound().orElseThrow();
    }

    private Round scheduledRound() {
        return roundRepository.findScheduledRound().orElseThrow();
    }

    private long balance() {
        return walletRepository.findById(playerId).orElseThrow().balance();
    }
}
