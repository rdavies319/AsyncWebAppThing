package com.rdavies.arena.fight.records;

/**
 * An exchange as anyone may see it: who hit, for how much, and the HP after. No raw rolls.
 *
 * @param redHit    whether red's attack landed; redDamage is what it did to blue
 */
public record ExchangeView(int n, boolean redHit, int redDamage, boolean blueHit, int blueDamage,
                           int redHpAfter, int blueHpAfter) {

    public static ExchangeView of(ExchangeResult exchange) {
        return new ExchangeView(exchange.n(), exchange.red().hit(), exchange.red().damage(),
                exchange.blue().hit(), exchange.blue().damage(), exchange.redHpAfter(), exchange.blueHpAfter());
    }
}
