package com.rdavies.arena.bet.records;

import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.fight.enums.Corner;

import java.time.Instant;

/**
 * One of a player's bets with its fight's matchup, for their bet history (GET /bets/me).
 *
 * @param startsAt the fight's start
 * @param red      the red corner's gladiator
 * @param payout   what settlement credited, stake included; null while pending
 */
public record BetRecord(long betId, long fightId, Instant startsAt, String red, String blue, Corner corner,
                        long stake, int multiplierBps, BetStatus status, Long payout, Instant createdAt) {

    /** The gladiator this bet backed. */
    public String backed() {
        return corner.pick(red, blue);
    }
}
