package com.rdavies.arena.leaderboard.records;

/** One row of the leaderboard: a player's place, public name and balance. */
public record LeaderboardEntry(int rank, String displayName, long balance) {}
