package com.rdavies.api.board;

import com.rdavies.api.GameProperties;
import com.rdavies.api.prediction.Prediction;
import com.rdavies.api.prediction.PredictionService;
import com.rdavies.api.round.RoundService;
import com.rdavies.api.round.RoundView;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.wallet.Wallet;
import com.rdavies.api.wallet.WalletService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Builds the game page's board (spec section 8): anyone can watch, and a logged-in player also sees their own part. */
@Service
public class BoardService {
    private final RoundService roundService;
    private final PredictionService predictionService;
    private final WalletService walletService;
    private final GameProperties game;

    public BoardService(RoundService roundService, PredictionService predictionService,
                        WalletService walletService, GameProperties game) {
        this.roundService = roundService;
        this.predictionService = predictionService;
        this.walletService = walletService;
        this.game = game;
    }

    /** The board as {@code viewer} sees it now; {@code viewer} is empty for an anonymous viewer. */
    public Board board(Optional<GamePrincipal> viewer) {
        // The view carries the server time and cutoff, so the page counts down by the server clock
        RoundView round = roundService.currentView().orElse(null);
        Board.LastRoll lastRoll = roundService.getLastSettledRound().map(last -> new Board.LastRoll(
                last.round().rollValue(), last.nextRoll(),
                viewer.flatMap(p -> predictionService.getPrediction(p.id(), last.round().id()))
                        .map(BoardService::outcome).orElse(null)))
                .orElse(null);
        Board.PlayerBoard player = viewer.map(p -> playerBoard(p, round)).orElse(null);
        return new Board(round, lastRoll, player, game.stakeOptions());
    }

    private Board.PlayerBoard playerBoard(GamePrincipal viewer, RoundView round) {
        Wallet wallet = walletService.getWallet(viewer.id());
        Board.Bet bet = round == null ? null : predictionService.getPrediction(viewer.id(), round.roundId())
                .map(p -> new Board.Bet(p.stake(), p.direction(), p.multiplier()))
                .orElse(null);
        Long topUpTarget = walletService.canTopUp(wallet) ? game.topUpTarget() : null;
        return new Board.PlayerBoard(viewer.displayName(), wallet.balance(), bet, topUpTarget);
    }

    /** What happened to a settled prediction's stake. */
    private static String outcome(Prediction prediction) {
        return switch (prediction.status()) {
            case WON -> "You won " + prediction.payout() + " pts";
            case LOST -> "You lost " + prediction.stake() + " pts";
            case PUSH -> "Tie: your " + prediction.stake() + " pts were refunded";
            case PENDING -> null;
        };
    }
}
