package com.rdavies.arena.web;

import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.user.CurrentUser;
import com.rdavies.arena.wallet.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The JSON API from spec section 9. Errors are ProblemDetails, see {@link GameApiExceptionHandler}. */
@RestController
public class GameApiController {
    private final FightService fightService;
    private final WalletService walletService;
    private final BetService betService;
    private final CurrentUser currentUser;

    public GameApiController(FightService fightService, WalletService walletService, BetService betService,
                             CurrentUser currentUser) {
        this.fightService = fightService;
        this.walletService = walletService;
        this.betService = betService;
        this.currentUser = currentUser;
    }

    @GetMapping("/fights/current")
    public CurrentFights currentFights() {
        return fightService.current();
    }

    /** POST /bets body. Any field may be missing; BetService refuses the bet with a 400 if one is. */
    public record PlaceBet(Long fightId, Corner corner, Long stake) {}

    @PostMapping("/bets")
    public Bet placeBet(@RequestBody PlaceBet request) {
        return betService.place(currentUser.id(), request.fightId(), request.corner(), request.stake());
    }

    public record TopUp(long credited, long balance) {}

    @PostMapping("/wallet/topup")
    public TopUp claimTopUp() {
        long credited = walletService.claimTopUp(currentUser.id());
        return new TopUp(credited, walletService.getWallet(currentUser.id()).balance());
    }
}
