package com.rdavies.arena.fight.records;

import java.time.Instant;
import java.util.List;

/**
 * The fight happening now, as anyone may see it.
 *
 * @param exchanges every exchange so far, oldest first
 */
public record LiveFight(long fightId, Instant startsAt, Fighter red, Fighter blue, int exchangesDone,
                        Instant nextExchangeAt, List<ExchangeView> exchanges) {
    public LiveFight {
        exchanges = List.copyOf(exchanges);
    }

    /**
     * One gladiator in a live fight. Starting HP is public once the fight starts (spec section 9,
     * fight_started), for the HP bars; no other stat is.
     */
    public record Fighter(String slug, String name, String epithet, int startingHp, int currentHp) {}
}
