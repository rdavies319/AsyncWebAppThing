package com.rdavies.api.fight;

import com.rdavies.api.GameTest;
import com.rdavies.api.TestGame;
import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;
import com.rdavies.api.fight.records.FightResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knockouts are stored with the right winner and ended_by (spec section 4, Ending). CapEndingTests
 * covers the cap.
 */
@GameTest
@Transactional
class FightEndingTests {
    @Autowired TestGame game;

    @Test
    void aKnockoutWins() {
        game.plantFight(Winner.BLUE, EndedBy.KO);

        FightResult result = game.finishFight();

        assertEquals(Winner.BLUE, result.winner());
        assertEquals(EndedBy.KO, result.endedBy());
        assertTrue(result.redHp() <= 0 && result.blueHp() > 0, result.toString());
    }

    @Test
    void aDoubleKnockoutIsADraw() {
        game.plantFight(Winner.DRAW, EndedBy.DOUBLE_KO);

        FightResult result = game.finishFight();

        assertEquals(Winner.DRAW, result.winner());
        assertEquals(EndedBy.DOUBLE_KO, result.endedBy());
        assertTrue(result.redHp() <= 0 && result.blueHp() <= 0, result.toString());
    }
}
