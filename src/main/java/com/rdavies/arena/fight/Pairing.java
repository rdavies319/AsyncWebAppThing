package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.RollKind;
import com.rdavies.arena.fight.records.Pair;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Who fights next (spec section 4, Pairing), as a pure function of the roster's history and the new fight's seed. */
final class Pairing {
    /** How many of red's latest opponents blue may not be. */
    static final int RECENT_OPPONENTS = 3;

    private Pairing() {}

    /**
     * Red is the free gladiator who has rested longest. Blue is the longest-rested of the rest who isn't
     * one of red's last {@link #RECENT_OPPONENTS} opponents, or of all the rest if every one of them is.
     * Ranking by rest alone would pair the same two forever: a pair who fought together rest together.
     *
     * @param roster          every gladiator id
     * @param lastFought      the starts_at of each gladiator's last fight; absent if they never fought
     * @param fighting        the gladiators in the fight happening now, who sit this one out
     * @param recentOpponents each gladiator's opponents, most recent first; absent if they never fought
     * @param seed            the new fight's seed, which breaks ties
     * @param fightId         the new fight's id
     */
    static Pair pick(List<Long> roster, Map<Long, Instant> lastFought, Set<Long> fighting,
                     Map<Long, List<Long>> recentOpponents, byte[] seed, long fightId) {
        List<Long> free = roster.stream().filter(id -> !fighting.contains(id)).sorted().toList();
        if (free.size() < 2) {
            throw new IllegalStateException("Not enough gladiators free to fight");
        }
        long red = longestRested(free, lastFought, Corner.RED, seed, fightId);

        List<Long> avoid = recentOpponents.getOrDefault(red, List.of()).stream().limit(RECENT_OPPONENTS).toList();
        List<Long> rest = free.stream().filter(id -> id != red).toList();
        List<Long> fresh = rest.stream().filter(id -> !avoid.contains(id)).toList();
        long blue = longestRested(fresh.isEmpty() ? rest : fresh, lastFought, Corner.BLUE, seed, fightId);
        return new Pair(red, blue);
    }

    /**
     * The candidate who has rested longest: never fought first, then the oldest last fight. Where
     * several tie, the seed picks one of them (ordered by id), rolling with the corner being filled.
     *
     * @param candidates in id order
     */
    private static long longestRested(List<Long> candidates, Map<Long, Instant> lastFought, Corner corner,
                                      byte[] seed, long fightId) {
        Instant oldest = candidates.stream().map(id -> rested(lastFought, id)).min(Instant::compareTo).orElseThrow();
        List<Long> tied = candidates.stream().filter(id -> rested(lastFought, id).equals(oldest)).toList();
        if (tied.size() == 1) {
            return tied.getFirst();
        }
        return tied.get(FightRolls.roll(seed, fightId, 0, corner, RollKind.PAIR, tied.size()) - 1);
    }

    /** When the gladiator last fought, or Instant.MIN if never, so they rank first. */
    private static Instant rested(Map<Long, Instant> lastFought, long id) {
        return Objects.requireNonNullElse(lastFought.get(id), Instant.MIN);
    }
}
