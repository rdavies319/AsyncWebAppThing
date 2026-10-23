package com.rdavies.arena.fight.records;

import java.util.List;

/**
 * A gladiator's public page (GET /gladiators/{slug}): who they are, their record, how they have
 * done against each opponent, and their last 5 results. Never their stats.
 */
public record GladiatorProfile(String slug, String name, String epithet, String bio, int elo, Career record,
                               List<HeadToHead> headToHead, List<RecentResult> lastResults) {
    public GladiatorProfile {
        headToHead = List.copyOf(headToHead);
        lastResults = List.copyOf(lastResults);
    }
}
