package com.rdavies.arena.fight.records;

/** A gladiator's record against one opponent. */
public record HeadToHead(String opponentSlug, String opponentName, int wins, int losses, int draws) {}
