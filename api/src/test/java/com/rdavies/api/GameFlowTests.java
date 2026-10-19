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
import com.rdavies.api.round.RoundRepository;
import com.rdavies.api.round.RoundRolled;
import com.rdavies.api.round.RoundSettled;
import com.rdavies.api.round.RoundStatus;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.TestWallets;
import com.rdavies.api.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static com.rdavies.api.TestGame.ROUND_START;
import static com.rdavies.api.TestGame.allowedDirection;
import static com.rdavies.api.TestHtml.isChecked;
import static com.rdavies.api.TestHtml.isDisabled;
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

    @Autowired TestGame game;
    @Autowired UserService userService;
    @Autowired PredictionService predictionService;
    @Autowired RoundRepository roundRepository;
    @Autowired PredictionRepository predictionRepository;
    @Autowired WalletService walletService;
    @Autowired TestWallets wallets;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationEvents events;
    @Autowired LiveStreams liveStreams;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @BeforeEach
    void openRound() {
        player = TestPlayers.signUp(userService, "player");
        playerId = player.id();
        game.open();
    }

    @Test
    void bootstrapOpensARolledRoundWithTheNextOneScheduled() {
        Round first = game.current();
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.startsAt());
        assertEquals(ProvablyFair.roll(first.seed(), first.id()), first.rollValue());

        Round next = game.scheduled();
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), next.startsAt());
        assertEquals(ProvablyFair.commit(next.seed()), next.seedCommitHash());
        assertNull(next.revealedSeed());
        assertNull(next.rollValue());
        assertEquals(2, roundRepository.count());
    }

    @Test
    void liveStreamStartsWithAServerTimeAndRoundSnapshot() throws Exception {
        game.startFrom(99);
        Round round = game.current();
        MvcResult result = mockMvc.perform(get("/live")).andExpect(request().asyncStarted()).andReturn();
        String body = result.getResponse().getContentAsString();

        assertTrue(body.startsWith("event:server_time\ndata:{\"server_time\":\"2026-01-01T00:01:00.200Z\"}"), body);
        assertTrue(body.contains("event:round_open\ndata:{\"round_id\":" + round.id() + ",\"roll\":99,"), body);
        assertTrue(body.contains("\"cutoff_at\":\"2026-01-01T00:01:50Z\""), body);
        // R = 99: Higher pays 98x with its stake capped at 102, Lower pays 0.98 / 0.98 = 1x
        assertTrue(body.contains("\"higher\":{\"multiplier_bps\":980000,\"max_stake\":102}"), body);
        assertTrue(body.contains("\"lower\":{\"multiplier_bps\":10000,\"max_stake\":500}"), body);
        assertTrue(body.contains("\"next_seed_commit_hash\":\"" + game.scheduled().seedCommitHash() + "\""), body);

        // Later events are sent as JSON serialised once, and reach the stream as is
        liveStreams.broadcast("round_settled", "{\"round_id\":1}");
        body = result.getResponse().getContentAsString();
        assertTrue(body.endsWith("event:round_settled\ndata:{\"round_id\":1}\n\n"), body);
    }

    @Test
    void settlingPublishesTheRollAndEachPlayersResult() {
        game.startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, game.current().id(), PredictionType.HIGHER, 100L);
        Round next = game.scheduled();
        events.clear();

        game.plantNextRoll(80);
        game.rollNext();

        Round rolled = roundRepository.findById(next.id()).orElseThrow();
        List<RoundRolled> published = events.stream(RoundRolled.class).toList();
        assertEquals(1, published.size()); // one event per roll
        RoundRolled roll = published.get(0);
        assertEquals(new RoundSettled(placed.roundId(), next.id(), 80, rolled.seed()), roll.settled());
        assertEquals(List.of(new PredictionSettled(playerId, placed.id(), placed.roundId(), PredictionStatus.WON, 196, 1096)),
                roll.predictions());
        RoundView opened = roll.opened();
        assertEquals(next.id(), opened.roundId());
        assertEquals(80, opened.roll());
        assertEquals(game.scheduled().seedCommitHash(), opened.nextSeedCommitHash());
    }

    @Test
    void gamePageShowsEachSidesOddsAndListensForNewRounds() throws Exception {
        game.startFrom(50);
        String page = mockMvc.perform(get("/").with(user(player))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("sse-connect=\"/live\""), page);
        assertTrue(page.contains("hx-trigger=\"sse:round_open\""), page);
        assertTrue(page.contains("1.96x · max 500"), page);
        assertTrue(page.contains("2.00x · max 500"), page);
        // Stake buttons, the smallest picked to start with
        assertTrue(isChecked(page, "stake", "10"), page);
        assertFalse(isChecked(page, "stake", "100"), page);
        assertFalse(isChecked(page, "stake", "500"), page);
    }

    @Test
    void anImpossibleSideIsDisabled() throws Exception {
        game.startFrom(100);
        String page = mockMvc.perform(get("/").with(user(player))).andReturn().getResponse().getContentAsString();
        assertTrue(isDisabled(page, "prediction", "HIGHER"), page);
        assertFalse(isDisabled(page, "prediction", "LOWER"), page);
    }

    @Test
    void eachStakeButtonListsTheSidesItFits() throws Exception {
        game.startFrom(99); // the payout cap limits HIGHER to 102
        String page = mockMvc.perform(get("/").with(user(player))).andReturn().getResponse().getContentAsString();
        assertTrue(TestHtml.input(page, "stake", "100").contains("data-sides=\"HIGHER LOWER\""), page);
        assertTrue(TestHtml.input(page, "stake", "500").contains("data-sides=\"LOWER\""), page);
        assertFalse(isDisabled(page, "stake", "500"), page); // still fine on LOWER until a side is picked
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
        game.startFrom(50);
        String board = mockMvc.perform(post("/predict").with(user(player)).with(csrf())
                        .param("roundId", game.current().id().toString())
                        .param("prediction", "LOWER").param("stake", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.contains("Your bet:"), board);
        assertTrue(board.contains("Bet placed"), board);
        assertTrue(board.contains("at <span>2.00x</span>"), board);
        assertTrue(board.contains("<span>900</span> pts"), board);
    }

    @Test
    void aRejectedPredictionShowsTheErrorOnTheBoard() throws Exception {
        game.startFrom(99); // HIGHER pays 98x, so the payout cap limits its stake to 102
        String board = mockMvc.perform(post("/predict").with(user(player)).with(csrf())
                        .param("roundId", game.current().id().toString())
                        .param("prediction", "HIGHER").param("stake", "500"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(board.contains("Stake must be between 10 and 102 pts on HIGHER"), board);
        // Keeps the stake and side the player picked
        assertTrue(isChecked(board, "stake", "500"), board);
        assertTrue(isChecked(board, "prediction", "HIGHER"), board);
        assertEquals(1000, balance());
    }

    @Test
    void gamePageRendersTheCurrentRound() throws Exception {
        Round round = game.current();
        mockMvc.perform(get("/").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"" + round.id() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        game.scheduled().seedCommitHash())));
    }

    @Test
    void gamePageShowsTheLastRollAndYourBet() throws Exception {
        Round round = game.current();
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L);
        mockMvc.perform(get("/").with(user(player)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your bet:")));

        game.rollNext();

        mockMvc.perform(get("/").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>" + game.current().rollValue() + "</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Your bet:"))));
    }

    @Test
    void aWinPaysTheMultiplierLockedInWhenPlaced() {
        game.startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, game.current().id(), PredictionType.HIGHER, 100L);
        assertEquals(19_600, placed.multiplierBps());
        assertEquals(900, balance());

        game.plantNextRoll(80);
        game.rollNext();

        Prediction settled = predictionRepository.findById(placed.id()).orElseThrow();
        assertEquals(PredictionStatus.WON, settled.status());
        assertEquals(196, settled.payout());
        assertEquals(1096, balance());
        assertEquals(196, jdbc.queryForObject(
                "SELECT delta FROM ledger WHERE reason = 'PAYOUT' AND ref_id = ?", Long.class, placed.id()));
        game.assertLedgerMatchesWallet(playerId);
    }

    @Test
    void aLossKeepsTheStake() {
        game.startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, game.current().id(), PredictionType.HIGHER, 100L);

        game.plantNextRoll(20);
        game.rollNext();

        Prediction settled = predictionRepository.findById(placed.id()).orElseThrow();
        assertEquals(PredictionStatus.LOST, settled.status());
        assertEquals(0, settled.payout());
        assertEquals(900, balance());
        assertEquals(2, ledgerRows()); // signup bonus and stake only
        game.assertLedgerMatchesWallet(playerId);
    }

    @Test
    void aTieRefundsTheStake() {
        game.startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, game.current().id(), PredictionType.LOWER, 100L);

        game.plantNextRoll(50);
        game.rollNext();

        Prediction settled = predictionRepository.findById(placed.id()).orElseThrow();
        assertEquals(PredictionStatus.PUSH, settled.status());
        assertEquals(100, settled.payout());
        assertEquals(1000, balance());
        assertEquals(100, jdbc.queryForObject(
                "SELECT delta FROM ledger WHERE reason = 'REFUND' AND ref_id = ?", Long.class, placed.id()));
        game.assertLedgerMatchesWallet(playerId);
    }

    @Test
    void impossibleDirectionsAreRejected() {
        game.startFrom(100);
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, game.current().id(), PredictionType.HIGHER, 10L));
        assertEquals(1000, balance());
    }

    @Test
    void negativeAndZeroStakesAreRejected() {
        Round round = game.current();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), -1000L));
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 0L));
        assertEquals(1000, balance());
    }

    @Test
    void predictionsCloseTenSecondsBeforeTheRoll() {
        Round round = game.current();
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
        Round round = game.current();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 9L));
        assertEquals(1000, balance());
    }

    @Test
    void stakesAboveTheMaximumAreRejected() {
        game.startFrom(50);
        long roundId = game.current().id();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 501L));
        predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 500L);
        assertEquals(500, balance());
    }

    @Test
    void thePayoutCapLowersTheMaxStakeOnLongShots() {
        game.startFrom(99); // HIGHER pays 98x, so the 10,000 cap allows at most 102
        long roundId = game.current().id();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 103L));
        predictionService.placePrediction(playerId, roundId, PredictionType.HIGHER, 102L);
        assertEquals(898, balance());
    }

    @Test
    void insufficientPointsAreRejected() {
        wallets.spend(playerId, 950);
        Round round = game.current();
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 100L));
        assertEquals(50, balance());
        assertEquals(2, ledgerRows()); // the signup bonus and the spend: no STAKE row for the refused bet
        // The prediction row was saved before the debit failed. In production the throw rolls it back, but this
        // test runs inside its own transaction, which the failed call only marks rollback-only.
    }

    @Test
    void settlementPaysTheStoredMultiplierNotTheCurrentOdds() {
        game.startFrom(50);
        Prediction placed = predictionService.placePrediction(playerId, game.current().id(), PredictionType.HIGHER, 100L);
        // Stands in for odds that changed after the prediction was placed
        jdbc.update("UPDATE predictions SET multiplier_bps = 30000 WHERE id = ?", placed.id());

        game.plantNextRoll(80);
        game.rollNext();

        assertEquals(300, predictionRepository.findById(placed.id()).orElseThrow().payout());
    }

    @Test
    void secondPredictionInARoundIsRejected() {
        Round round = game.current();
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L);
        assertThrows(GameRuleException.class,
                () -> predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 10L));
        assertEquals(990, balance());
    }

    @Test
    void repeatTicksNeverRollOrPayTwice() {
        Round round = game.current();
        predictionService.placePrediction(playerId, round.id(), allowedDirection(round), 100L);

        game.rollNext();
        long balanceAfterSettle = balance();
        int ledgerRowsAfterSettle = ledgerRows();
        Round next = game.current();

        game.tickAt(ROUND_START.plusSeconds(60)); // the same tick again

        game.tickAt(ROUND_START.plusSeconds(75));

        assertEquals(balanceAfterSettle, balance());
        assertEquals(ledgerRowsAfterSettle, ledgerRows());
        assertEquals(next.id(), game.current().id());
        assertEquals(3, roundRepository.count()); // settled, open, scheduled
        game.assertLedgerMatchesWallet(playerId);
    }

    @Test
    void roundsOpenOnTheMinuteAndRollAtTheNext() {
        Round first = game.current();
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.startsAt());

        // A slightly late tick still settles, and the next round keeps its minute slot
        game.tickAt(ROUND_START.plusSeconds(70));
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(first.id()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), game.current().startsAt());
        assertEquals(Instant.parse("2026-01-01T00:02:00Z"), game.scheduled().startsAt());
    }

    @Test
    void aTickFiringJustBeforeTheMinuteCountsAsOnIt() {
        Round first = game.current();

        // Well before the minute, nothing is due
        game.tickAt(Instant.parse("2026-01-01T00:00:58Z"));
        assertEquals(first.id(), game.current().id());

        // The scheduler fires a few ms early: the roll still happens, and the next round takes the
        // coming minute rather than the one ending, so it gets its full betting window
        game.tickAt(Instant.parse("2026-01-01T00:00:59.995Z"));
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(first.id()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), game.current().startsAt());

        // And the next early tick, a minute on, rolls that one
        Round second = game.current();
        game.tickAt(Instant.parse("2026-01-01T00:01:59.995Z"));
        assertEquals(RoundStatus.SETTLED, roundRepository.findById(second.id()).orElseThrow().status());
        assertEquals(Instant.parse("2026-01-01T00:02:00Z"), game.current().startsAt());
    }

    @Test
    void roundMissedDuringDowntimeIsSettledWithItsCommittedSeed() {
        Round stale = game.current();
        Round committed = game.scheduled();
        Prediction placed = predictionService.placePrediction(playerId, stale.id(), allowedDirection(stale), 100L);

        game.tickAt(Instant.parse("2026-01-01T00:10:20Z"));

        assertEquals(RoundStatus.SETTLED, roundRepository.findById(stale.id()).orElseThrow().status());
        assertNotEquals(PredictionStatus.PENDING, predictionRepository.findById(placed.id()).orElseThrow().status());
        game.assertLedgerMatchesWallet(playerId);

        // The committed round opens on the current minute with the roll its seed always implied
        Round reopened = game.current();
        assertEquals(committed.id(), reopened.id());
        assertEquals(Instant.parse("2026-01-01T00:10:00Z"), reopened.startsAt());
        assertEquals(ProvablyFair.roll(committed.seed(), committed.id()), reopened.rollValue());
        assertEquals(Instant.parse("2026-01-01T00:11:00Z"), game.scheduled().startsAt());
        assertEquals(3, roundRepository.count()); // no rounds for the missed minutes

        // Catching up again changes nothing
        long balance = balance();
        game.tickAt(Instant.parse("2026-01-01T00:10:20Z"));
        assertEquals(reopened.id(), game.current().id());
        assertEquals(balance, balance());
    }

    @Test
    void rollIsCommittedBeforeAndVerifiableAfter() {
        Round next = game.scheduled();
        assertNotNull(next.seedCommitHash());
        assertNull(next.revealedSeed());

        game.rollNext();

        Round rolled = roundRepository.findById(next.id()).orElseThrow();
        String seed = rolled.revealedSeed();
        assertNotNull(seed);
        assertEquals(next.seedCommitHash(), ProvablyFair.commit(seed));
        assertEquals(ProvablyFair.roll(seed, rolled.id()), rolled.rollValue());
    }

    @Test
    void rollsCannotBeRewritten() {
        Round round = game.current();
        int otherRoll = round.rollValue() == 1 ? 2 : 1;
        DataAccessException e = refusedByDatabase("UPDATE rounds SET roll_value = ? WHERE id = ?", otherRoll, round.id());
        assertTrue(e.getMostSpecificCause().getMessage().contains("immutable"), e.getMessage());
    }

    @Test
    void ledgerIsAppendOnly() {
        DataAccessException e = refusedByDatabase("DELETE FROM ledger WHERE user_id = ?", playerId);
        assertTrue(e.getMostSpecificCause().getMessage().contains("append-only"), e.getMessage());
    }

    @Test
    void settlesAThousandPredictionsInUnderTwoSeconds() {
        game.startFrom(50);
        long roundId = game.current().id();
        // 1,000 players who each staked 100 of their 1,000 points: half HIGHER, half LOWER
        jdbc.update("""
                INSERT INTO users (email, display_name, password_hash)
                SELECT 'load' || g || '@example.invalid', 'load' || g, '!' FROM generate_series(1, 1000) g""");
        wallets.openWalletsForUsers("load%", 1000);
        jdbc.update("""
                INSERT INTO predictions (user_id, round_id, direction, stake, multiplier_bps, status, created_at)
                SELECT id, ?, CASE WHEN id % 2 = 0 THEN 'HIGHER' ELSE 'LOWER' END, 100, 19600, 'PENDING', now()
                FROM users WHERE email LIKE 'load%'""", roundId);
        wallets.debitPendingStakes(roundId);

        game.plantNextRoll(70);
        long began = System.nanoTime();
        game.rollNext();
        long millis = (System.nanoTime() - began) / 1_000_000;

        assertTrue(millis < 2_000, "settlement took " + millis + " ms");
        assertEquals(500, jdbc.queryForObject(
                "SELECT COUNT(*) FROM predictions WHERE round_id = ? AND status = 'WON'", Integer.class, roundId));
        assertEquals(500, jdbc.queryForObject(
                "SELECT COUNT(*) FROM predictions WHERE round_id = ? AND status = 'LOST'", Integer.class, roundId));
        wallets.assertEveryWalletMatchesItsLedger();
    }

    /**
     * Runs a statement the database must refuse, inside a savepoint: a failed statement aborts a
     * Postgres transaction, and this keeps the test transaction usable afterwards.
     */
    private DataAccessException refusedByDatabase(String sql, Object... args) {
        TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        return assertThrows(DataAccessException.class,
                () -> savepoint.executeWithoutResult(status -> jdbc.update(sql, args)));
    }

    private int ledgerRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger WHERE user_id = ?", Integer.class, playerId);
    }

    private long balance() {
        return walletService.getWallet(playerId).balance();
    }
}
