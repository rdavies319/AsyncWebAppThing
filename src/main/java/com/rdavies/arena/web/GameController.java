package com.rdavies.arena.web;

import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.board.BoardService;
import com.rdavies.arena.board.records.BoardFeedback;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.user.CurrentUser;
import com.rdavies.arena.wallet.WalletService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The game page. htmx swaps the {@code board} fragment, which the top-up and bet forms return so
 * their outcome shows inline. The JSON API is GameApiController.
 */
@Controller
public class GameController {
    private final BoardService boardService;
    private final WalletService walletService;
    private final BetService betService;
    private final CurrentUser currentUser;

    public GameController(BoardService boardService, WalletService walletService, BetService betService,
                          CurrentUser currentUser) {
        this.boardService = boardService;
        this.walletService = walletService;
        this.betService = betService;
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

    /** The board's stake buttons. The same service as POST /bets, with any refusal shown on the board. */
    @PostMapping("/bet")
    public String bet(@RequestParam(name = "fight_id", required = false) Long fightId,
                      @RequestParam(required = false) String corner,
                      @RequestParam(required = false) Long stake,
                      Model model) {
        BoardFeedback feedback = BoardFeedback.none();
        try {
            betService.place(currentUser.id(), fightId, Corner.fromLabel(corner), stake);
        } catch (GameRuleException e) {
            feedback = BoardFeedback.betRefused(e.getMessage());
        }
        addBoard(model, feedback);
        return "game :: board";
    }

    private void addBoard(Model model, BoardFeedback feedback) {
        model.addAttribute("board", boardService.board(currentUser.principal()));
        model.addAttribute("feedback", feedback);
    }
}
