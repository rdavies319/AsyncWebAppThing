package com.rdavies.api.fight;

import com.rdavies.api.GameProperties;
import com.rdavies.api.fight.enums.FightStatus;
import com.rdavies.api.fight.records.ExchangeResult;
import com.rdavies.api.fight.records.Fight;
import com.rdavies.api.fight.records.Outcome;
import com.rdavies.api.gladiator.GladiatorService;
import com.rdavies.api.gladiator.records.Gladiator;
import com.rdavies.api.gladiator.records.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * The fight engine (spec section 7). Each tick is one transaction under the arena's advisory lock,
 * and every step is gated on fight status and exchanges_done, so a repeated tick changes nothing.
 */
@Component
public class FightEngine {
    private static final Logger log = LoggerFactory.getLogger(FightEngine.class);
    /** The arena's advisory lock key ("ARENA" in ASCII). */
    private static final long LOCK_KEY = 0x4152454E41L;

    private final FightRepository fights;
    private final FightExchangeRepository exchanges;
    private final GladiatorService gladiators;
    private final GameProperties game;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final CronExpression fightCron;
    private final SecureRandom random = new SecureRandom();

    FightEngine(FightRepository fights, FightExchangeRepository exchanges, GladiatorService gladiators,
                GameProperties game, Clock clock, JdbcTemplate jdbc) {
        this.fights = fights;
        this.exchanges = exchanges;
        this.gladiators = gladiators;
        this.game = game;
        this.clock = clock;
        this.jdbc = jdbc;
        this.fightCron = CronExpression.parse(game.fightCron());
    }

    @Transactional
    public void tick() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY))) {
            log.debug("Another tick holds the arena lock; skipping");
            return;
        }
        Instant now = clock.instant();
        fights.lockLive().ifPresent(live -> runDueExchanges(live, now));

        Optional<Fight> announced = fights.lockAnnounced();
        boolean fighting = fights.findLive().isPresent();
        if (announced.isPresent()) {
            if (!announced.get().startsAt().isAfter(now) && !fighting) {
                start(announced.get());
            }
        } else if (!fighting) {
            // Bootstrap, and for now also the next fight once the last has settled
            announce(nextSlot(now));
        }
    }

    private void runDueExchanges(Fight fight, Instant now) {
        Stats red = gladiators.stats(fight.redId());
        Stats blue = gladiators.stats(fight.blueId());
        while (fight.status() == FightStatus.LIVE && !fight.nextExchangeAt().isAfter(now)) {
            int n = fight.exchangesDone() + 1;
            ExchangeResult exchange = Combat.exchange(fight.seed(), fight.id(), n, red, blue, fight.redHp(), fight.blueHp());
            exchanges.insert(fight.id(), exchange, fight.nextExchangeAt(), now);
            Optional<Outcome> outcome = Combat.ending(n, game.maxExchanges(), red, blue,
                    exchange.redHpAfter(), exchange.blueHpAfter());
            fight = outcome.isPresent()
                    ? fight.settle(exchange, outcome.get(), now)
                    : fight.afterExchange(exchange, dueAt(fight, n + 1));
            fights.save(fight);
        }
        if (fight.status() == FightStatus.SETTLED) {
            log.info("Fight {} settled: {} by {} after {} exchanges", fight.id(), fight.winner(), fight.endedBy(), fight.exchangesDone());
        }
    }

    private void start(Fight fight) {
        fights.save(fight.start(dueAt(fight, 1)));
        log.info("Fight {} started", fight.id());
    }

    /** Announces a fight for {@code slot}: for now, the two gladiators with the lowest ids. */
    private void announce(Instant slot) {
        List<Gladiator> pair = gladiators.roster().stream().limit(2).toList();
        if (pair.size() < 2) {
            throw new IllegalStateException("The roster needs at least two gladiators");
        }
        Gladiator red = pair.get(0);
        Gladiator blue = pair.get(1);
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        Fight fight = fights.save(Fight.announce(slot, red.id(), blue.id(), red.elo(), blue.elo(), seed,
                gladiators.stats(red.id()).hp(), gladiators.stats(blue.id()).hp()));
        log.info("Fight {} announced for {}: {} v {}", fight.id(), slot, red.slug(), blue.slug());
    }

    /** Exchange n is due at starts_at + n × exchange-interval. */
    private Instant dueAt(Fight fight, int n) {
        return fight.startsAt().plus(game.exchangeInterval().multipliedBy(n));
    }

    private Instant nextSlot(Instant after) {
        return fightCron.next(after.atZone(ZoneOffset.UTC)).toInstant();
    }
}
