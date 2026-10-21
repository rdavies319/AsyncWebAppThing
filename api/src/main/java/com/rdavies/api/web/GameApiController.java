package com.rdavies.api.web;

import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.wallet.WalletService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The JSON API. Errors are ProblemDetails, see {@link GameApiExceptionHandler}. */
@RestController
public class GameApiController {
    private final WalletService walletService;
    private final CurrentUser currentUser;

    public GameApiController(WalletService walletService, CurrentUser currentUser) {
        this.walletService = walletService;
        this.currentUser = currentUser;
    }

    public record TopUp(long credited, long balance) {}

    @PostMapping("/wallet/topup")
    public TopUp claimTopUp() {
        long credited = walletService.claimTopUp(currentUser.id());
        return new TopUp(credited, walletService.getWallet(currentUser.id()).balance());
    }
}
