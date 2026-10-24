package com.rdavies.arena.bet.events;

import com.rdavies.arena.bet.enums.BetStatus;

/**
 * A bet was settled (SSE bet_settled, sent only to its owner).
 *
 * @param payout  what was credited, stake included: the win, the refunded stake on a draw, or 0
 * @param balance the owner's balance straight after settlement
 */
public record BetSettled(long userId, long betId, long fightId, BetStatus status, long payout, long balance) {}
