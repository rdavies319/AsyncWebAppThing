package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.enums.Winner;

import java.time.Instant;

/**
 * A fight in the public history (GET /fights): the matchup with its ratings and odds at
 * announcement, and the result once settled. Built from public columns only.
 *
 * @param winner     null until settled
 * @param roundsDone the rounds fought so far
 */
public record FightRecord(long fightId, Instant startsAt, FightStatus status, Side red, Side blue,
                          Winner winner, EndedBy endedBy, int roundsDone) {

    public Side side(Corner corner) {
        return corner == Corner.RED ? red : blue;
    }

    /**
     * @param elo           the rating at announcement
     * @param multiplierBps what a winning bet on this corner paid per point staked, in basis points
     */
    public record Side(String slug, String name, String epithet, int elo, int multiplierBps) {}
}
