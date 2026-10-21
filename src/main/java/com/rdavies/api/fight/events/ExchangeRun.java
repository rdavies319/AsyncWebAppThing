package com.rdavies.api.fight.events;

import com.rdavies.api.fight.records.ExchangeView;

/** An exchange ran (SSE exchange): who hit, for how much, and the HP after. No raw rolls. */
public record ExchangeRun(long fightId, int n, boolean redHit, int redDamage, boolean blueHit, int blueDamage,
                          int redHpAfter, int blueHpAfter) {

    public static ExchangeRun of(long fightId, ExchangeView exchange) {
        return new ExchangeRun(fightId, exchange.n(), exchange.redHit(), exchange.redDamage(),
                exchange.blueHit(), exchange.blueDamage(), exchange.redHpAfter(), exchange.blueHpAfter());
    }
}
