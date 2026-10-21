package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Stats;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test-only access to fight rows, for TestGame. Lives in the fight feature because planting a
 * seed needs the combat core, which nothing outside the feature may call.
 */
public class TestFights {
    private static final int SEARCH_LIMIT = 200_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final GladiatorService gladiators;
    private final GameProperties game;

    TestFights(JdbcTemplate jdbc, GladiatorService gladiators, GameProperties game) {
        this.jdbc = jdbc;
        this.gladiators = gladiators;
        this.game = game;
    }

    /**
     * Makes the announced fight end as wanted, by replacing it with the same fight on a seed found
     * by search. Stands in for chance: the engine still plays the fight out from its seed.
     */
    public void plant(Winner winner, EndedBy endedBy) {
        Map<String, Object> fight = jdbc.queryForMap("SELECT * FROM fights WHERE status = 'ANNOUNCED'");
        long id = (Long) fight.get("id");
        Stats red = gladiators.stats((Long) fight.get("red_id"));
        Stats blue = gladiators.stats((Long) fight.get("blue_id"));
        Outcome wanted = new Outcome(winner, endedBy);
        byte[] seed = new byte[32];
        for (int i = 0; i < SEARCH_LIMIT; i++) {
            RANDOM.nextBytes(seed);
            if (Combat.simulate(seed, id, red, blue, game.maxRounds()).outcome().equals(wanted)) {
                // The seed is immutable, so the row is replaced rather than updated
                jdbc.update("DELETE FROM fights WHERE id = ?", id);
                jdbc.update("""
                        INSERT INTO fights (id, starts_at, status, red_id, blue_id, red_elo, blue_elo, seed, red_hp, blue_hp)
                        VALUES (?, ?, 'ANNOUNCED', ?, ?, ?, ?, ?, ?, ?)""",
                        id, fight.get("starts_at"), fight.get("red_id"), fight.get("blue_id"), fight.get("red_elo"),
                        fight.get("blue_elo"), seed, fight.get("red_hp"), fight.get("blue_hp"));
                return;
            }
        }
        fail("no seed in " + SEARCH_LIMIT + " tries gives " + wanted);
    }

    /**
     * Removes every fight, for tests that commit. TRUNCATE skips the append-only trigger, which
     * blocks deleting rounds row by row.
     */
    public void deleteAll() {
        jdbc.execute("TRUNCATE fight_rounds, fights");
    }
}
