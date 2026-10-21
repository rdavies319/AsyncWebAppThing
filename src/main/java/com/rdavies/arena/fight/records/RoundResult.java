package com.rdavies.arena.fight.records;

/**
 * One round: both attacks, resolved together from the state before it.
 *
 * @param n    the round number, from 1
 * @param red  red's attack on blue
 * @param blue blue's attack on red
 */
public record RoundResult(int n, Attack red, Attack blue, int redHpAfter, int blueHpAfter) {}
