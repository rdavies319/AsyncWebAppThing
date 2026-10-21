package com.rdavies.api.fight;

import com.rdavies.api.GameTest;
import com.rdavies.api.TestGame;
import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.Winner;
import com.rdavies.api.fight.records.FightResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fights that reach the cap are decided on HP share (spec section 4, Ending). The dev roster almost
 * never lasts 30 exchanges, so these run with a cap of one, which no knockout can beat; that costs
 * this class its own Spring context.
 */
@GameTest
@Transactional
@TestPropertySource(properties = "game.max-exchanges=1")
class CapEndingTests {
    @Autowired TestGame game;

    @Test
    void theHigherShareOfHpWins() {
        game.plantFight(Winner.RED, EndedBy.CAP);

        FightResult result = game.finishFight();

        assertEquals(Winner.RED, result.winner());
        assertEquals(EndedBy.CAP, result.endedBy());
        assertEquals(1, result.exchangesDone());
    }

    @Test
    void equalSharesAreADraw() {
        game.plantFight(Winner.DRAW, EndedBy.CAP);

        FightResult result = game.finishFight();

        assertEquals(Winner.DRAW, result.winner());
        assertEquals(EndedBy.CAP, result.endedBy());
    }
}
