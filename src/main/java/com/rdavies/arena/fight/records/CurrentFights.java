package com.rdavies.arena.fight.records;

import java.time.Instant;

/**
 * What GET /fights/current shows (spec section 9).
 *
 * @param serverTime the server clock, so pages count down by it rather than their own
 * @param live       the fight happening now; null between fights
 * @param announced  the next fight, open for betting; null until one is announced
 */
public record CurrentFights(Instant serverTime, LiveFight live, AnnouncedFight announced) {}
