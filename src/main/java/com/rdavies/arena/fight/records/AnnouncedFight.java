package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.Corner;

import java.time.Instant;

/**
 * The next fight, open for betting, as anyone may see it.
 *
 * @param cutoffAt betting closes here: game.cutoff before the start
 */
public record AnnouncedFight(long fightId, Instant startsAt, Instant cutoffAt, Fighter red, Fighter blue) {

    public Fighter fighter(Corner corner) {
        return corner == Corner.RED ? red : blue;
    }

    /**
     * @param elo           the rating stored at announcement
     * @param multiplierBps what a winning bet on this corner pays per point staked, stake included, in basis points
     * @param maxStake      the largest stake on this corner: game.max-stake, lowered by the payout cap
     */
    public record Fighter(String slug, String name, String epithet, int elo, int multiplierBps, long maxStake) {}
}
