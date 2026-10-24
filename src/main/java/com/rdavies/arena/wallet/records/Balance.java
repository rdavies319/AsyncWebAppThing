package com.rdavies.arena.wallet.records;

/** One player's balance, by user id: the wallet knows nothing else about its owner. */
public record Balance(long userId, long balance) {}
