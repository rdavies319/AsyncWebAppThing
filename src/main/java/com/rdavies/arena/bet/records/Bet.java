package com.rdavies.arena.bet.records;

import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.fight.enums.Corner;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * One player's bet on one fight. Final once placed: only settlement changes it.
 *
 * @param multiplierBps the corner's odds when the bet was placed, in basis points; a win pays stake × this / 10000
 * @param payout        what settlement credited, stake included (0 for a loss); null while pending
 */
@Table("bets")
public record Bet(
    @Id Long id,
    long userId,
    long fightId,
    Corner corner,
    long stake,
    int multiplierBps,
    BetStatus status,
    Long payout,
    Instant createdAt,
    Instant settledAt
) {}
