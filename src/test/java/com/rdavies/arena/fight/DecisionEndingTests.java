package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.FightResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fights that reach the cap are decided on HP share (spec section 4, Ending). The dev roster almost
 * never lasts 30 rounds, so these run with a cap of one, which no knockout can beat; that costs
 * this class its own Spring context.
 */
@GameTest
@Transactional
@TestPropertySource(properties = "game.max-rounds=1")
class DecisionEndingTests {
    @Autowired TestGame game;

    @Test
    void theHigherShareOfHpWins() {
        game.plantFight(Winner.RED, EndedBy.DECISION);

        FightResult result = game.finishFight();

        assertEquals(Winner.RED, result.winner());
        assertEquals(EndedBy.DECISION, result.endedBy());
        assertEquals(1, result.roundsDone());
    }

    @Test
    void equalSharesAreADraw() {
        game.plantFight(Winner.DRAW, EndedBy.DECISION);

        FightResult result = game.finishFight();

        assertEquals(Winner.DRAW, result.winner());
        assertEquals(EndedBy.DECISION, result.endedBy());
    }
}
