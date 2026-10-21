package com.rdavies.api.fight.records;

import java.time.Instant;

/** The next fight, open for betting, as anyone may see it. */
public record AnnouncedFight(long fightId, Instant startsAt, Fighter red, Fighter blue) {

    /** @param elo the rating stored at announcement */
    public record Fighter(String slug, String name, String epithet, int elo) {}
}
