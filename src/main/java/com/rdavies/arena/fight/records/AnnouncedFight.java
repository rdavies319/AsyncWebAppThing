package com.rdavies.arena.fight.records;

import java.time.Instant;

/** The next fight, open for betting, as anyone may see it. */
public record AnnouncedFight(long fightId, Instant startsAt, Fighter red, Fighter blue) {

    /**
     * @param elo           the rating stored at announcement
     * @param multiplierBps what a winning bet on this corner pays per point staked, stake included, in basis points
     * @param maxStake      the largest stake on this corner: game.max-stake, lowered by the payout cap
     */
    public record Fighter(String slug, String name, String epithet, int elo, int multiplierBps, long maxStake) {}
}
