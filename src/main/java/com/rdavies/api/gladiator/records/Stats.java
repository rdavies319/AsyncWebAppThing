package com.rdavies.api.gladiator.records;

/**
 * A gladiator's hidden stat block (spec section 3). Public only so the {@code fight} feature can
 * run combat: it must never reach an API response, page, event or log line.
 *
 * @param hp        starting hit points
 * @param attackMin smallest damage roll per hit, before defence
 * @param attackMax largest damage roll per hit, before defence
 * @param defence   flat reduction to every hit taken; a hit always does at least 1
 * @param agility   percent chance to dodge an incoming attack
 */
public record Stats(int hp, int attackMin, int attackMax, int defence, int agility) {
    public Stats {
        if (hp < 1 || attackMin < 1 || attackMax < attackMin || defence < 0 || agility < 0 || agility > 100) {
            // The values themselves are secret, so the message doesn't carry them
            throw new IllegalArgumentException("invalid stat block");
        }
    }

    @Override
    public String toString() {
        return "Stats[hidden]";
    }
}
