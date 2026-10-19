package com.rdavies.api;

import com.rdavies.api.prediction.Prediction;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionSettled;
import com.rdavies.api.prediction.PredictionStatus;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.prediction.Settlement;
import com.rdavies.api.round.Odds;
import com.rdavies.api.round.Round;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Settlement through its one call, against the real settlement SQL (spec section 3). The engine's
 * timing around it is covered by GameFlowTests.
 */
@GameTest
@Transactional
class SettlementTests {
    @Autowired Settlement settlement;
    @Autowired TestGame game;
    @Autowired UserService userService;
    @Autowired PredictionService predictionService;
    @Autowired WalletService walletService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void winnersArePaidAtTheirLockedMultiplierAndLosersGetNothing() {
        Round round = game.startFrom(50); // HIGHER 1.96x, LOWER 2.00x
        Prediction alice = place("alice", round, PredictionType.HIGHER, 100);
        Prediction bob = place("bob", round, PredictionType.LOWER, 100);

        List<PredictionSettled> settled = settle(round, 80);

        assertEquals(List.of(
                new PredictionSettled(alice.userId(), alice.id(), round.id(), PredictionStatus.WON, 196, 1096),
                new PredictionSettled(bob.userId(), bob.id(), round.id(), PredictionStatus.LOST, 0, 900)),
                settled);
        assertEquals(Odds.payout(100, 19_600), settled.get(0).payout());
        assertLedger(alice, bob);
    }

    @Test
    void lowerWinsWhenTheRollFalls() {
        Round round = game.startFrom(50);
        Prediction alice = place("alice", round, PredictionType.HIGHER, 100);
        Prediction bob = place("bob", round, PredictionType.LOWER, 100);

        settle(round, 20);

        assertSettled(alice, PredictionStatus.LOST, 0, 900);
        assertSettled(bob, PredictionStatus.WON, 200, 1100);
        assertLedger(alice, bob);
    }

    @Test
    void aTieRefundsEveryStake() {
        Round round = game.startFrom(50);
        Prediction alice = place("alice", round, PredictionType.HIGHER, 100);
        Prediction bob = place("bob", round, PredictionType.LOWER, 10);

        settle(round, 50);

        assertSettled(alice, PredictionStatus.PUSH, 100, 1000);
        assertSettled(bob, PredictionStatus.PUSH, 10, 1000);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ledger WHERE reason = 'REFUND' AND ref_id IN (?, ?)",
                Integer.class, alice.id(), bob.id()));
        assertLedger(alice, bob);
    }

    @Test
    void payoutsRoundDown() {
        Round round = game.startFrom(4); // LOWER: 3 winning rolls, 980000 / 3 = 326666 bps
        Prediction alice = place("alice", round, PredictionType.LOWER, 10);

        settle(round, 2);

        // 10 × 32.6666 = 326.67, paid as 326
        assertSettled(alice, PredictionStatus.WON, Odds.payout(10, 326_666), 990 + 326);
        assertEquals(326, Odds.payout(10, 326_666));
        assertLedger(alice);
    }

    @Test
    void theLargestLongShotStaysWithinTheMaxPayout() {
        Round round = game.startFrom(99); // HIGHER pays 98x; the 10,000 cap allows a stake of 102
        Prediction alice = place("alice", round, PredictionType.HIGHER, 102);

        settle(round, 100);

        assertSettled(alice, PredictionStatus.WON, 9_996, 898 + 9_996);
        assertEquals(Odds.payout(102, 980_000), 9_996);
        assertLedger(alice);
    }

    @Test
    void aRoundWithNoPredictionsSettlesNothing() {
        Round round = game.startFrom(50);
        int ledgerRows = ledgerRows();

        assertEquals(List.of(), settle(round, 80));
        assertEquals(ledgerRows, ledgerRows());
    }

    @Test
    void settlingTheSameRoundTwicePaysOnce() {
        Round round = game.startFrom(50);
        Prediction alice = place("alice", round, PredictionType.HIGHER, 100);
        settle(round, 80);
        int ledgerRows = ledgerRows();

        assertEquals(List.of(), settle(round, 80));

        assertSettled(alice, PredictionStatus.WON, 196, 1096);
        assertEquals(ledgerRows, ledgerRows());
        assertLedger(alice);
    }

    @Test
    void onlyTheSettledRoundIsTouched() {
        Round round = game.startFrom(50);
        Prediction alice = place("alice", round, PredictionType.HIGHER, 100);
        Round other = game.rollNext(); // settles round, opening the next one
        Prediction bob = place("bob", other, TestGame.allowedDirection(other), 10);

        settle(round, 80); // round has nothing pending any more

        assertEquals(PredictionStatus.PENDING, status(bob));
        assertNotEquals(PredictionStatus.PENDING, status(alice));
    }

    private Prediction place(String name, Round round, PredictionType direction, long stake) {
        long userId = TestPlayers.signUp(userService, name).id();
        return predictionService.placePrediction(userId, round.id(), direction, stake);
    }

    private List<PredictionSettled> settle(Round round, int nextRoll) {
        Instant rollTime = round.rollTime();
        return settlement.settle(round.id(), round.rollValue(), nextRoll, rollTime);
    }

    private void assertSettled(Prediction placed, PredictionStatus status, long payout, long balance) {
        assertEquals(status, status(placed));
        assertEquals(payout, jdbc.queryForObject("SELECT payout FROM predictions WHERE id = ?", Long.class, placed.id()));
        assertEquals(balance, walletService.getWallet(placed.userId()).balance());
    }

    private PredictionStatus status(Prediction prediction) {
        return PredictionStatus.valueOf(
                jdbc.queryForObject("SELECT status FROM predictions WHERE id = ?", String.class, prediction.id()));
    }

    private void assertLedger(Prediction... predictions) {
        for (Prediction p : predictions) {
            game.assertLedgerMatchesWallet(p.userId());
        }
    }

    private int ledgerRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger", Integer.class);
    }
}
