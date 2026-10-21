package com.rdavies.arena.fight.enums;

/** What a roll decides, which keeps the rolls for one exchange independent of each other (spec section 6). */
public enum RollKind {
    /** Whether an attack lands: 1..100, dodged at or below the defender's agility. */
    HIT,
    /** The damage roll within the attacker's attack range. */
    DMG,
    /** The pairing tie-break, rolled as exchange 0. */
    PAIR;

    public String label() {
        return name().toLowerCase();
    }
}
