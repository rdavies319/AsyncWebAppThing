package com.rdavies.api.board;

import com.rdavies.api.GameProperties;
import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.wallet.Wallet;
import com.rdavies.api.wallet.WalletService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Builds the game page's board: anyone can watch, and a logged-in player also sees their own part. */
@Service
public class BoardService {
    private final WalletService walletService;
    private final GameProperties game;

    public BoardService(WalletService walletService, GameProperties game) {
        this.walletService = walletService;
        this.game = game;
    }

    /** The board as {@code viewer} sees it now; {@code viewer} is empty for an anonymous viewer. */
    public Board board(Optional<GamePrincipal> viewer) {
        return new Board(viewer.map(this::playerBoard).orElse(null));
    }

    private Board.PlayerBoard playerBoard(GamePrincipal viewer) {
        Wallet wallet = walletService.getWallet(viewer.id());
        Long topUpTarget = walletService.canTopUp(wallet) ? game.topUpTarget() : null;
        return new Board.PlayerBoard(viewer.displayName(), wallet.balance(), topUpTarget);
    }
}
