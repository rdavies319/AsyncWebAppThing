package com.rdavies.arena.fight.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CornerTests {

    @Test
    void eachCornerPicksItsOwnSide() {
        assertEquals("Brutus", Corner.RED.pick("Brutus", "Vex"));
        assertEquals("Vex", Corner.BLUE.pick("Brutus", "Vex"));
    }
}
