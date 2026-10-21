package com.rdavies.arena.gladiator;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.gladiator.records.Gladiator;
import com.rdavies.arena.gladiator.records.Stats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Loading the roster on first boot (spec section 3), and reading gladiators back. */
@GameTest
@Transactional
class GladiatorServiceTests {
    @Autowired GladiatorService gladiators;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theDevRosterLoadedOnBoot() {
        List<Gladiator> roster = gladiators.roster();

        assertEquals(8, roster.size());
        assertEquals("brutus", roster.getFirst().slug());
        assertEquals("the Anvil", roster.getFirst().epithet());
        assertEquals(1000, roster.getFirst().elo());
    }

    @Test
    void aLaterBootLeavesTheRosterAlone() {
        String other = """
                [{"slug": "intruder", "name": "Intruder", "epithet": "the Late", "bio": "Too late.",
                  "stats": {"hp": 90, "attack_min": 5, "attack_max": 12, "defence": 1, "agility": 25}}]""";

        assertFalse(gladiators.loadRosterIfEmpty(new ByteArrayResource(other.getBytes(StandardCharsets.UTF_8))));

        assertEquals(8, gladiators.roster().size());
    }

    @Test
    void anEmptyTableLoadsTheRosterFile() {
        jdbc.update("DELETE FROM gladiators"); // rolled back after the test
        String roster = """
                [{"slug": "vex", "name": "Vex", "epithet": "the Quick", "bio": "Fast.",
                  "stats": {"hp": 90, "attack_min": 5, "attack_max": 12, "defence": 1, "agility": 25}}]""";

        assertTrue(gladiators.loadRosterIfEmpty(new ByteArrayResource(roster.getBytes(StandardCharsets.UTF_8))));

        Gladiator vex = gladiators.roster().getFirst();
        assertEquals("Vex", vex.name());
        assertEquals(new Stats(90, 5, 12, 1, 25), gladiators.stats(vex.id()));
    }

    @Test
    void statsCannotChange() {
        long id = gladiators.roster().getFirst().id();

        assertThrows(Exception.class, () -> jdbc.update("UPDATE gladiators SET hp = hp + 1 WHERE id = ?", id));
    }
}
