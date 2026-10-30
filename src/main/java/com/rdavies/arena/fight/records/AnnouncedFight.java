package com.rdavies.arena.fight.records;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rdavies.arena.fight.enums.Corner;

import java.time.Instant;
import java.util.List;

/**
 * The next fight, open for betting, as anyone may see it.
 *
 * @param cutoffAt    betting closes here: game.cutoff before the start
 * @param bettingOpen whether the cutoff was still ahead when this was read; kept out of JSON, where it
 *                    would go stale (clients count down to cutoff_at)
 */
public record AnnouncedFight(long fightId, Instant startsAt, Instant cutoffAt, @JsonIgnore boolean bettingOpen,
                             Fighter red, Fighter blue) {

    public Fighter fighter(Corner corner) {
        return corner == Corner.RED ? red : blue;
    }

    /**
     * @param elo           the rating stored at announcement
     * @param multiplierBps what a winning bet on this corner pays per point staked, stake included, in basis points
     * @param stakes        the stakes a bet on this corner may have: game.stake-options, less any the payout cap rules out
     */
    public record Fighter(String slug, String name, String epithet, int elo, int multiplierBps, List<Long> stakes) {
        public Fighter {
            stakes = List.copyOf(stakes);
        }
    }
}
