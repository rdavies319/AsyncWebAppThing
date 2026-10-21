package com.rdavies.arena.fight.records;

/**
 * One exchange: both attacks, resolved together from the state before it.
 *
 * @param n    the exchange number, from 1
 * @param red  red's attack on blue
 * @param blue blue's attack on red
 */
public record ExchangeResult(int n, Attack red, Attack blue, int redHpAfter, int blueHpAfter) {}
