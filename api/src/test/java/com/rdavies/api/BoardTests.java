package com.rdavies.api;

import com.rdavies.api.board.Board;
import com.rdavies.api.board.BoardService;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.prediction.PredictionType;
import com.rdavies.api.round.Round;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** What each viewer sees on the game page, checked on the Board value rather than the rendered HTML. */
@GameTest
@Transactional
class BoardTests {
    @Autowired BoardService boardService;
    @Autowired TestGame game;
    @Autowired UserService userService;
    @Autowired PredictionService predictionService;
    @Autowired WalletService walletService;

    @Test
    void beforeTheFirstRoundThereIsNothingToShow() {
        Board board = boardService.board(Optional.empty());

        assertNull(board.round());
        assertNull(board.lastRoll());
        assertNull(board.player());
        assertFalse(board.canPredict());
        assertEquals(List.of(10L, 100L, 500L), board.stakeOptions());
    }

    @Test
    void anAnonymousViewerSeesTheRoundButNothingPersonal() {
        Round round = game.startFrom(50);

        Board board = boardService.board(Optional.empty());

        assertEquals(round.id(), board.round().roundId());
        assertEquals(50, board.round().roll());
        assertEquals(50, board.lastRoll().nextRoll());
        assertNull(board.lastRoll().outcome());
        assertNull(board.player());
        assertFalse(board.canPredict());
    }

    @Test
    void aPlayerWithoutABetCanPredict() {
        game.startFrom(50);
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");

        Board board = boardService.board(Optional.of(alice));

        assertEquals("alice", board.player().name());
        assertEquals(1000, board.player().balance());
        assertNull(board.player().bet());
        assertNull(board.player().topUpTarget()); // at the top-up target already
        assertTrue(board.canPredict());
    }

    @Test
    void everyStakeFitsBothSidesOnAnEvenRound() {
        game.startFrom(50); // max stake 500 on both sides
        Board board = boardService.board(Optional.of(TestPlayers.signUp(userService, "alice")));

        assertEquals(Map.of(PredictionType.HIGHER, List.of(10L, 100L, 500L), PredictionType.LOWER, List.of(10L, 100L, 500L)),
                board.player().stakesBySide());
        assertEquals(List.of(PredictionType.HIGHER, PredictionType.LOWER), board.sidesAllowing(500));
        assertTrue(board.stakeAllowed(500, null));
    }

    @Test
    void thePayoutCapRulesOutBigStakesOnALongShot() {
        game.startFrom(99); // HIGHER pays 98x, so its max stake is 102; LOWER keeps 500
        Board board = boardService.board(Optional.of(TestPlayers.signUp(userService, "alice")));

        assertEquals(List.of(10L, 100L), board.player().stakesBySide().get(PredictionType.HIGHER));
        assertEquals(List.of(PredictionType.LOWER), board.sidesAllowing(500));
        assertFalse(board.stakeAllowed(500, PredictionType.HIGHER));
        assertTrue(board.stakeAllowed(500, PredictionType.LOWER));
        assertTrue(board.stakeAllowed(500, null)); // allowed until a side rules it out
    }

    @Test
    void anImpossibleSideAllowsNoStake() {
        game.startFrom(100); // nothing is higher than 100
        Board board = boardService.board(Optional.of(TestPlayers.signUp(userService, "alice")));

        assertFalse(board.player().stakesBySide().containsKey(PredictionType.HIGHER));
        assertEquals(List.of(PredictionType.LOWER), board.sidesAllowing(10));
    }

    @Test
    void stakesAboveTheBalanceAreRuledOut() {
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");
        loseStake(alice, 50, 500, 20); // 1000 → 500
        loseStake(alice, 20, 100, 10); // 500 → 400

        Board board = boardService.board(Optional.of(alice));

        assertEquals(400, board.player().balance());
        assertEquals(List.of(), board.sidesAllowing(500));
        assertFalse(board.stakeAllowed(500, null));
        assertTrue(board.stakeAllowed(100, null));
    }

    @Test
    void anAnonymousViewerCanPlaceNoStake() {
        game.startFrom(50);
        Board board = boardService.board(Optional.empty());

        assertEquals(List.of(), board.sidesAllowing(10));
        assertFalse(board.stakeAllowed(10, null));
    }

    @Test
    void aPlayerWithABetSeesItAndCannotPredictAgain() {
        Round round = game.startFrom(50);
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");
        predictionService.placePrediction(alice.id(), round.id(), PredictionType.LOWER, 100L);

        Board board = boardService.board(Optional.of(alice));

        assertEquals(new Board.Bet(100, PredictionType.LOWER, "2.00x"), board.player().bet());
        assertEquals(900, board.player().balance());
        assertEquals(1000L, board.player().topUpTarget()); // below the target, not claimed today
        assertFalse(board.canPredict());
    }

    @Test
    void theTopUpIsOfferedOnlyUntilItIsClaimed() {
        Round round = game.startFrom(50);
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");
        predictionService.placePrediction(alice.id(), round.id(), PredictionType.LOWER, 100L);
        walletService.claimTopUp(alice.id());

        assertNull(boardService.board(Optional.of(alice)).player().topUpTarget());
    }

    @Test
    void theLastRollShowsEachPlayersOutcome() {
        Round round = game.startFrom(50);
        GamePrincipal winner = TestPlayers.signUp(userService, "winner");
        GamePrincipal loser = TestPlayers.signUp(userService, "loser");
        GamePrincipal bystander = TestPlayers.signUp(userService, "bystander");
        predictionService.placePrediction(winner.id(), round.id(), PredictionType.HIGHER, 100L);
        predictionService.placePrediction(loser.id(), round.id(), PredictionType.LOWER, 100L);
        game.plantNextRoll(80);
        game.rollNext();

        assertEquals(new Board.LastRoll(50, 80, "You won 196 pts"), boardService.board(Optional.of(winner)).lastRoll());
        assertEquals(new Board.LastRoll(50, 80, "You lost 100 pts"), boardService.board(Optional.of(loser)).lastRoll());
        assertEquals(new Board.LastRoll(50, 80, null), boardService.board(Optional.of(bystander)).lastRoll());
        assertNull(boardService.board(Optional.of(winner)).player().bet()); // the bet was on the last round
    }

    @Test
    void aTieShowsTheRefund() {
        Round round = game.startFrom(50);
        GamePrincipal alice = TestPlayers.signUp(userService, "alice");
        predictionService.placePrediction(alice.id(), round.id(), PredictionType.HIGHER, 100L);
        game.plantNextRoll(50);
        game.rollNext();

        assertEquals("Tie: your 100 pts were refunded", boardService.board(Optional.of(alice)).lastRoll().outcome());
    }

    /** Opens a round on {@code roll}, bets {@code stake} on HIGHER and rolls {@code nextRoll} (below it), so the stake is lost. */
    private void loseStake(GamePrincipal player, int roll, long stake, int nextRoll) {
        Round round = game.startFrom(roll);
        predictionService.placePrediction(player.id(), round.id(), PredictionType.HIGHER, stake);
        game.plantNextRoll(nextRoll);
        game.rollNext();
    }
}
