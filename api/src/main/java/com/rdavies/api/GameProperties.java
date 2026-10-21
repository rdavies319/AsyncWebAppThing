package com.rdavies.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Game settings, overridable as game.* properties.
 *
 * @param cutoff       betting on a fight closes this long before it starts
 * @param minStake     smallest stake accepted
 * @param maxStake     largest stake accepted, before the payout cap lowers it
 * @param maxPayout    most one bet can pay out, stake included
 * @param signupBonus  points credited to a new wallet
 * @param topUpTarget  the daily top-up refills a wallet to this balance
 * @param stakeOptions the stakes the game page offers as buttons, each within minStake..maxStake
 */
@ConfigurationProperties("game")
public record GameProperties(
    @DefaultValue("10s") Duration cutoff,
    @DefaultValue("10") long minStake,
    @DefaultValue("500") long maxStake,
    @DefaultValue("10000") long maxPayout,
    @DefaultValue("1000") long signupBonus,
    @DefaultValue("1000") long topUpTarget,
    @DefaultValue({"10", "100", "500"}) List<Long> stakeOptions
) {
    /** Checked at startup, so a stake button the server would always refuse can't ship. */
    public GameProperties {
        if (stakeOptions.isEmpty()) {
            throw new IllegalArgumentException("game.stake-options must list at least one stake");
        }
        for (long stake : stakeOptions) {
            if (stake < minStake || stake > maxStake) {
                throw new IllegalArgumentException("game.stake-options: " + stake
                        + " is outside game.min-stake..game.max-stake (" + minStake + ".." + maxStake + ")");
            }
        }
        stakeOptions = List.copyOf(stakeOptions);
    }
}
