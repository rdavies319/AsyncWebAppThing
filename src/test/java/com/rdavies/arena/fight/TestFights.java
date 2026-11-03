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
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final TransactionTemplate transactions;

    TestFights(JdbcTemplate jdbc, FightRepository fights, GladiatorService gladiators, GameProperties game,
               TransactionTemplate transactions) {
        this.transactions = transactions;
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

    /** What a test does while {@link #whileEngineLocked} holds the arena lock. */
    public interface WhileLocked {
        void run() throws Exception;
    }

    /**
     * Runs {@code action} while another transaction, on its own thread, holds the arena's advisory
     * lock, and returns once that's released. The caller must not be @Transactional, because the
     * test transaction would hold the lock itself.
     */
    public void whileEngineLocked(WhileLocked action) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = other.submit(() -> transactions.executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, FightEngine.LOCK_KEY);
                locked.countDown();
                awaitRelease(release);
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "the other transaction took the lock");
            action.run();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
