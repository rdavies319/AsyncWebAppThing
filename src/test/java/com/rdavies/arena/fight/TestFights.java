package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.fight.records.SimulatedFight;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Stats;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test-only access to fight rows, for TestGame. Lives in the fight feature because planting a
 * seed needs the combat core, which nothing outside the feature may call.
 */
public class TestFights {
    private static final int SEARCH_LIMIT = 200_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final FightRepository fights;
    private final GladiatorService gladiators;
    private final GameProperties game;

    TestFights(JdbcTemplate jdbc, FightRepository fights, GladiatorService gladiators, GameProperties game) {
        this.jdbc = jdbc;
        this.fights = fights;
        this.gladiators = gladiators;
        this.game = game;
    }

    /** A settled fight's result, with the HP each side finished on. */
    public FightResult result(long fightId) {
        Fight fight = fights.findById(fightId).orElseThrow(() -> new AssertionError("No fight " + fightId));
        assertNotNull(fight.winner(), "fight " + fightId + " hasn't settled");
        return new FightResult(fight.id(), fight.winner(), fight.endedBy(), fight.roundsDone(), fight.redHp(), fight.blueHp());
    }

    /**
     * Makes the announced fight end as wanted, by replacing it with the same fight on a seed found
     * by search. Stands in for chance: the engine still plays the fight out from its seed.
     */
    public void plant(Winner winner, EndedBy endedBy) {
        Outcome wanted = new Outcome(winner, endedBy);
        plant(simulated -> simulated.outcome().equals(wanted), String.valueOf(wanted));
    }

    /** As {@link #plant(Winner, EndedBy)}, for a fight lasting at least {@code rounds} rounds. */
    public void plantLasting(int rounds) {
        plant(simulated -> simulated.rounds().size() >= rounds, "a fight of " + rounds + "+ rounds");
    }

    private void plant(Predicate<SimulatedFight> wanted, String description) {
        Map<String, Object> fight = jdbc.queryForMap("SELECT * FROM fights WHERE status = 'ANNOUNCED'");
        long id = (Long) fight.get("id");
        Stats red = gladiators.stats((Long) fight.get("red_id"));
        Stats blue = gladiators.stats((Long) fight.get("blue_id"));
        byte[] seed = new byte[32];
        for (int i = 0; i < SEARCH_LIMIT; i++) {
            RANDOM.nextBytes(seed);
            if (wanted.test(Combat.simulate(seed, id, red, blue, game.maxRounds()))) {
                // The seed is immutable, so the row is replaced rather than updated
                jdbc.update("DELETE FROM fights WHERE id = ?", id);
                jdbc.update("""
                        INSERT INTO fights (id, starts_at, status, red_id, blue_id, red_elo, blue_elo,
                                            red_multiplier_bps, blue_multiplier_bps, seed, red_hp, blue_hp)
                        VALUES (?, ?, 'ANNOUNCED', ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        id, fight.get("starts_at"), fight.get("red_id"), fight.get("blue_id"), fight.get("red_elo"),
                        fight.get("blue_elo"), fight.get("red_multiplier_bps"), fight.get("blue_multiplier_bps"),
                        seed, fight.get("red_hp"), fight.get("blue_hp"));
                return;
            }
        }
        fail("no seed in " + SEARCH_LIMIT + " tries gives " + description);
    }

    /**
     * Removes every fight, and every bet on one, and puts every rating back to its starting 1000, for
     * tests that commit. Wallets and their ledger rows stay, so they still match.
     * TRUNCATE skips the append-only trigger, which blocks deleting rounds row by row.
     */
    public void deleteAll() {
        jdbc.execute("TRUNCATE bets, fight_rounds, fights");
        jdbc.update("UPDATE gladiators SET elo = 1000");
    }
}
