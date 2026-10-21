package com.rdavies.api.fight;

import com.rdavies.api.GameTest;
import com.rdavies.api.TestGame;
import com.rdavies.api.fight.enums.FightStatus;
import com.rdavies.api.fight.records.AnnouncedFight;
import com.rdavies.api.fight.records.CurrentFights;
import com.rdavies.api.fight.records.ExchangeResult;
import com.rdavies.api.fight.records.Fight;
import com.rdavies.api.fight.records.FightResult;
import com.rdavies.api.fight.records.LiveFight;
import com.rdavies.api.fight.records.SimulatedFight;
import com.rdavies.api.gladiator.GladiatorService;
import com.rdavies.api.gladiator.records.Gladiator;
import com.rdavies.api.gladiator.records.Stats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The engine tick from spec section 7, driven through the real engine with the test clock. */
@GameTest
@Transactional
class FightEngineTests {
    private static final Instant ONE_AM = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant TWO_AM = Instant.parse("2026-01-01T02:00:00Z");

    @Autowired TestGame game;
    @Autowired FightService fightService;
    @Autowired FightRepository fights;
    @Autowired FightExchangeRepository exchanges;
    @Autowired GladiatorService gladiators;
    @Autowired JdbcTemplate jdbc;

    @Test
    void bootstrapAnnouncesTheFirstFightForTheNextSlot() {
        CurrentFights now = game.boot();

        assertNull(now.live());
        assertEquals(ONE_AM, now.announced().startsAt());
        Set<String> slugs = gladiators.roster().stream().map(Gladiator::slug).collect(Collectors.toSet());
        assertTrue(slugs.contains(now.announced().red().slug()));
        assertTrue(slugs.contains(now.announced().blue().slug()));
        assertNotEquals(now.announced().red().slug(), now.announced().blue().slug());
    }

    @Test
    void aFightWaitsForItsSlot() {
        game.boot();

        CurrentFights now = game.tickAt(ONE_AM.minusMillis(1));

        assertNull(now.live());
        assertNotNull(now.announced());
    }

    @Test
    void atItsSlotTheFightGoesLive() {
        LiveFight live = game.startFight();

        assertEquals(ONE_AM, live.startsAt());
        assertEquals(0, live.exchangesDone());
        assertEquals(ONE_AM.plusSeconds(60), live.nextExchangeAt());
        assertEquals(live.red().startingHp(), live.red().currentHp());
        assertTrue(live.exchanges().isEmpty());
    }

    @Test
    void whenAFightStartsTheNextIsAnnouncedWithoutItsFighters() {
        LiveFight live = game.startFight();

        AnnouncedFight next = fightService.current().announced();

        assertEquals(TWO_AM, next.startsAt());
        Set<String> fighting = Set.of(live.red().slug(), live.blue().slug());
        assertFalse(fighting.contains(next.red().slug()));
        assertFalse(fighting.contains(next.blue().slug()));
    }

    @Test
    void theNextFightWaitsForTheLastToSettle() {
        LiveFight live = game.startFight();
        // Hold the live fight past the next slot, as a fight too long for its slot would
        jdbc.update("UPDATE fights SET next_exchange_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-01-01T05:00:00Z")), live.fightId());

        CurrentFights now = game.tickAt(TWO_AM);

        assertEquals(live.fightId(), now.live().fightId());
        assertEquals(Instant.parse("2026-01-01T03:00:00Z"), now.announced().startsAt()); // moved to the following slot
    }

    @Test
    void eachDueExchangeIsWrittenWithTheNewHp() {
        LiveFight started = game.startFight();

        LiveFight live = game.nextExchange().live();

        assertEquals(1, live.exchangesDone());
        assertEquals(ONE_AM.plusSeconds(120), live.nextExchangeAt());
        assertEquals(1, live.exchanges().size());
        assertEquals(started.red().startingHp() - live.exchanges().getFirst().blueDamage(), live.red().currentHp());
        assertEquals(started.blue().startingHp() - live.exchanges().getFirst().redDamage(), live.blue().currentHp());
    }

    @Test
    void anExchangeWaitsUntilItIsDue() {
        LiveFight live = game.startFight();

        assertEquals(0, game.tickAt(live.nextExchangeAt().minusMillis(1)).live().exchangesDone());
    }

    @Test
    void repeatingATickChangesNothing() {
        game.startFight();
        CurrentFights once = game.nextExchange();

        CurrentFights twice = game.tickAt(once.serverTime());

        assertEquals(once, twice);
    }

    @Test
    void aFinishedFightSettlesAndTheNextStartsAtItsSlot() {
        FightResult result = game.finishFight();
        Fight settled = fights.findById(result.fightId()).orElseThrow();

        assertEquals(FightStatus.SETTLED, settled.status());
        assertNotNull(result.winner());
        assertNotNull(result.endedBy());
        assertNull(settled.nextExchangeAt());
        CurrentFights now = fightService.current();
        assertNull(now.live());
        assertEquals(TWO_AM, now.announced().startsAt());
        assertEquals(TWO_AM, game.startFight().startsAt());
    }

    @Test
    void theFightPlaysOutExactlyAsTheCombatCoreSimulatesItsSeed() {
        FightResult result = game.finishFight();
        Fight fight = fights.findById(result.fightId()).orElseThrow();
        Stats red = gladiators.stats(fight.redId());
        Stats blue = gladiators.stats(fight.blueId());

        SimulatedFight simulated = Combat.simulate(fight.seed(), fight.id(), red, blue, 30);

        assertEquals(simulated.outcome().winner(), result.winner());
        assertEquals(simulated.outcome().endedBy(), result.endedBy());
        assertEquals(simulated.exchanges().size(), result.exchangesDone());
        List<ExchangeResult> stored = exchanges.findByFight(fight.id()).stream().map(e -> e.result()).toList();
        assertEquals(simulated.exchanges(), stored);
        ExchangeResult last = simulated.exchanges().getLast();
        assertEquals(last.redHpAfter(), result.redHp());
        assertEquals(last.blueHpAfter(), result.blueHp());
    }
}
