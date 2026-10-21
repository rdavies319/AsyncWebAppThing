package com.rdavies.api.fight.records;

/**
 * One gladiator's attack in an exchange.
 *
 * @param hitRoll the raw 1..100 hit roll (internal)
 * @param dmgRoll the raw damage roll (internal); null when the attack was dodged
 * @param hit     whether the attack landed
 * @param damage  the damage done; 0 when dodged
 */
public record Attack(int hitRoll, Integer dmgRoll, boolean hit, int damage) {}
