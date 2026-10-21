package com.rdavies.api.web;

import com.rdavies.api.GameRuleException;
import com.rdavies.api.board.BoardService;
import com.rdavies.api.board.records.BoardFeedback;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.wallet.WalletService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The game page. htmx swaps the {@code board} fragment, which the top-up form returns so its
 * outcome shows inline. The JSON API is GameApiController.
 */
@Controller
public class GameController {
    private final BoardService boardService;
    private final WalletService walletService;
    private final CurrentUser currentUser;

    public GameController(BoardService boardService, WalletService walletService, CurrentUser currentUser) {
        this.boardService = boardService;
        this.walletService = walletService;
        this.currentUser = currentUser;
    }

    @GetMapping("/")
    public String gameView(Model model) {
        addBoard(model, BoardFeedback.none());
        return "game";
    }

    @GetMapping("/fragments/board")
    public String board(Model model) {
        addBoard(model, BoardFeedback.none());
        return "game :: board";
    }

    @PostMapping("/topup")
    public String topUp(Model model) {
        BoardFeedback feedback = BoardFeedback.none();
        try {
            walletService.claimTopUp(currentUser.id());
        } catch (GameRuleException e) {
            feedback = BoardFeedback.topUpRefused(e.getMessage());
        }
        addBoard(model, feedback);
        return "game :: board";
    }

    private void addBoard(Model model, BoardFeedback feedback) {
        model.addAttribute("board", boardService.board(currentUser.principal()));
        model.addAttribute("feedback", feedback);
    }
}
