package com.rdavies.api.fight;

import com.rdavies.api.fight.enums.Corner;
import com.rdavies.api.fight.enums.RollKind;
import com.rdavies.api.fight.records.Pair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Who fights next (spec section 4, Pairing), as a pure function of the roster's history and the new fight's seed. */
final class Pairing {
    private Pairing() {}

    /**
     * @param roster      every gladiator id
     * @param lastFought  the starts_at of each gladiator's last fight; absent if they never fought
     * @param fighting    the gladiators in the fight happening now, who sit this one out
     * @param currentPair the pair fighting now, which must not fight again straight away
     * @param seed        the new fight's seed, which breaks ties
     * @param fightId     the new fight's id
     */
    static Pair pick(List<Long> roster, Map<Long, Instant> lastFought, Set<Long> fighting, Set<Long> currentPair,
                     byte[] seed, long fightId) {
        List<Long> ranked = rank(roster, lastFought, fighting, seed, fightId);
        if (ranked.size() < 2) {
            throw new IllegalStateException("Not enough gladiators free to fight");
        }
        long red = ranked.get(0);
        long blue = ranked.get(1);
        if (Set.of(red, blue).equals(currentPair) && ranked.size() > 2) {
            blue = ranked.get(2);
        }
        return new Pair(red, blue);
    }

    /**
     * Free gladiators in order: never fought first, then the longest since their last fight. Where a
     * tie straddles the top two, the seed picks from the tied group (ordered by id): red's pick rolls
     * with corner "red", blue's with "blue". The rest of the group follows in id order.
     */
    private static List<Long> rank(List<Long> roster, Map<Long, Instant> lastFought, Set<Long> fighting,
                                   byte[] seed, long fightId) {
        // Group by last fight, never-fought (Instant.MIN) first; each group in id order
        TreeMap<Instant, List<Long>> groups = new TreeMap<>();
        roster.stream().filter(id -> !fighting.contains(id)).sorted().forEach(id ->
                groups.computeIfAbsent(Objects.requireNonNullElse(lastFought.get(id), Instant.MIN), k -> new ArrayList<>()).add(id));

        List<Long> ranked = new ArrayList<>();
        for (List<Long> group : groups.values()) {
            List<Long> remaining = new ArrayList<>(group);
            while (ranked.size() < 2 && remaining.size() > 2 - ranked.size()) {
                Corner corner = ranked.isEmpty() ? Corner.RED : Corner.BLUE;
                int pick = Dice.roll(seed, fightId, 0, corner, RollKind.PAIR, remaining.size()) - 1;
                ranked.add(remaining.remove(pick));
            }
            remaining.sort(Comparator.naturalOrder());
            ranked.addAll(remaining);
        }
        return ranked;
    }
}
