package com.rdavies.api.web;

import com.rdavies.api.fight.FightService;
import com.rdavies.api.fight.records.CurrentFights;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.wallet.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The JSON API from spec section 9. Errors are ProblemDetails, see {@link GameApiExceptionHandler}. */
@RestController
public class GameApiController {
    private final FightService fightService;
    private final WalletService walletService;
    private final CurrentUser currentUser;

    public GameApiController(FightService fightService, WalletService walletService, CurrentUser currentUser) {
        this.fightService = fightService;
        this.walletService = walletService;
        this.currentUser = currentUser;
    }

    @GetMapping("/fights/current")
    public CurrentFights currentFights() {
        return fightService.current();
    }

    public record TopUp(long credited, long balance) {}

    @PostMapping("/wallet/topup")
    public TopUp claimTopUp() {
        long credited = walletService.claimTopUp(currentUser.id());
        return new TopUp(credited, walletService.getWallet(currentUser.id()).balance());
    }
}
