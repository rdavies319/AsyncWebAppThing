package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.gladiator.GladiatorService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Ratings and odds through the engine (spec section 5): fixed at announcement, moved at settlement. */
@GameTest
@Transactional
class FightOddsTests {
    @Autowired TestGame game;
    @Autowired FightRepository fights;
    @Autowired GladiatorService gladiators;
    @Autowired JdbcTemplate jdbc;

    @Test
    void anAnnouncedFightStoresBothRatingsAndTheOddsFromThem() {
        // A different rating for each gladiator, so whichever pair is drawn, the odds are uneven
        jdbc.update("UPDATE gladiators SET elo = 900 + 37 * id");

        Fight fight = row(game.boot().announced().fightId());

        int redElo = gladiators.gladiator(fight.redId()).elo();
        int blueElo = gladiators.gladiator(fight.blueId()).elo();
        assertEquals(redElo, fight.redElo());
        assertEquals(blueElo, fight.blueElo());
        int redChance = Odds.redChanceBps(redElo, blueElo);
        assertNotEquals(5000, redChance);
        assertEquals(Odds.multiplierBps(redChance), fight.redMultiplierBps());
        assertEquals(Odds.multiplierBps(10_000 - redChance), fight.blueMultiplierBps());
    }

    @ParameterizedTest
    @ValueSource(strings = {"red_elo", "blue_elo", "red_multiplier_bps", "blue_multiplier_bps"})
    void theStoredRatingsAndOddsCantChange(String column) {
        long id = game.boot().announced().fightId();

        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fights SET " + column + " = " + column + " + 1 WHERE id = ?", id));
    }

    @Test
    void aWinMovesBothRatingsWhenTheFightSettles() {
        game.plantFight(Winner.RED, EndedBy.KO);
        Fight fight = row(game.startFight().fightId());

        game.finishFight();

        assertEquals(1016, gladiators.gladiator(fight.redId()).elo()); // equals: half of K = 32
        assertEquals(984, gladiators.gladiator(fight.blueId()).elo());
    }

    @Test
    void aDrawBetweenEqualsLeavesBothRatings() {
        game.plantFight(Winner.DRAW, EndedBy.DOUBLE_KO);
        Fight fight = row(game.startFight().fightId());

        game.finishFight();

        assertEquals(1000, gladiators.gladiator(fight.redId()).elo());
        assertEquals(1000, gladiators.gladiator(fight.blueId()).elo());
    }

    @Test
    void theNextFightsOddsHoldWhileTheLastSettles() {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        Fight current = row(game.startFight().fightId());
        Fight next = fights.findAnnounced().orElseThrow();

        FightResult result = game.finishFight();

        assertEquals(current.id(), result.fightId());
        assertEquals(984, gladiators.gladiator(current.redId()).elo());
        Fight after = fights.findAnnounced().orElseThrow();
        assertEquals(next.id(), after.id());
        assertEquals(next.redMultiplierBps(), after.redMultiplierBps());
        assertEquals(next.blueMultiplierBps(), after.blueMultiplierBps());
        // Neither of the next fight's gladiators fought, so the odds still match their ratings
        assertEquals(after.redElo(), gladiators.gladiator(after.redId()).elo());
        assertEquals(after.blueElo(), gladiators.gladiator(after.blueId()).elo());
    }

    private Fight row(long fightId) {
        return fights.findById(fightId).orElseThrow();
    }
}
