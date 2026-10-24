package com.rdavies.arena.fight;

import com.rdavies.arena.DevGameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Under the dev profile, a fresh arena starts from a simulated season's ratings, so the first fights
 * already have uneven odds. Called directly here: DevGameTest turns the startup steps off.
 */
@DevGameTest
@Transactional
class DevRatingsTests {
    @Autowired DevRatings devRatings;
    @Autowired GladiatorService gladiators;
    @Autowired TestFights testFights;
    @Autowired TestGame game;

    @BeforeEach
    void freshArena() {
        testFights.deleteAll(); // rolled back after the test
    }

    @Test
    void aFreshArenaStartsFromTheSimulatedSeason() {
        assertTrue(devRatings.seedIfFresh());

        List<Gladiator> roster = gladiators.roster();
        List<Integer> expected = EloSeason.replay(roster.stream().map(g -> gladiators.stats(g.id())).toList(), 30);
        assertEquals(expected, roster.stream().map(Gladiator::elo).toList());
        assertNotEquals(Collections.nCopies(roster.size(), 1000), expected);
    }

    @Test
    void theFirstFightIsPricedFromTheSeededRatings() {
        devRatings.seedIfFresh();

        AnnouncedFight announced = game.boot().announced();

        assertEquals(gladiators.roster().stream().filter(g -> g.slug().equals(announced.red().slug()))
                .findFirst().orElseThrow().elo(), announced.red().elo());
    }

    @Test
    void anArenaThatHasFoughtKeepsItsRatings() {
        game.boot();

        assertFalse(devRatings.seedIfFresh());

        assertTrue(gladiators.roster().stream().allMatch(g -> g.elo() == 1000));
    }
}
