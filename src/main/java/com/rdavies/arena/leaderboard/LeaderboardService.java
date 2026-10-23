package com.rdavies.arena.leaderboard;

import com.rdavies.arena.leaderboard.records.LeaderboardEntry;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.WalletService;
import com.rdavies.arena.wallet.records.Balance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The top balances (spec section 9). The balances come from the wallet and the names from the user
 * feature, so this feature reads neither's tables.
 */
@Service
public class LeaderboardService {
    private final WalletService wallets;
    private final UserService users;

    public LeaderboardService(WalletService wallets, UserService users) {
        this.wallets = wallets;
        this.users = users;
    }

    /** The {@code limit} highest balances, highest first, ranked 1, 2, 3… with ties in the order wallets break them. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> top(int limit) {
        List<Balance> balances = wallets.highestBalances(limit);
        Map<Long, String> names = users.displayNames(balances.stream().map(Balance::userId).toList());
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Balance balance : balances) {
            entries.add(new LeaderboardEntry(entries.size() + 1, names.get(balance.userId()), balance.balance()));
        }
        return entries;
    }
}
