package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.events.ExchangeRun;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.fight.events.FightStarted;
import com.rdavies.arena.fight.events.MatchupAnnounced;
import com.rdavies.arena.fight.records.ExchangeResult;
import com.rdavies.arena.fight.records.ExchangeView;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.LastFought;
import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.fight.records.Pair;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import com.rdavies.arena.gladiator.records.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fight engine (spec section 7). Each tick is one transaction under the arena's advisory lock,
 * and every step is gated on fight status and exchanges_done, so a repeated tick changes nothing.
 * Events are published inside the tick and reach listeners only after it commits.
 */
@Component
public class FightEngine {
    private static final Logger log = LoggerFactory.getLogger(FightEngine.class);
    /** The arena's advisory lock key ("ARENA" in ASCII). */
    static final long LOCK_KEY = 0x4152454E41L;

    private final FightRepository fights;
    private final FightExchangeRepository exchanges;
    private final FightService views;
    private final GladiatorService gladiators;
    private final ApplicationEventPublisher events;
    private final GameProperties game;
    private final Clock clock;
    private final CronExpression fightCron;
    private final SecureRandom random = new SecureRandom();

    FightEngine(FightRepository fights, FightExchangeRepository exchanges, FightService views,
                GladiatorService gladiators, ApplicationEventPublisher events, GameProperties game,
                Clock clock) {
        this.fights = fights;
        this.exchanges = exchanges;
        this.views = views;
        this.gladiators = gladiators;
        this.events = events;
        this.game = game;
        this.clock = clock;
        this.fightCron = CronExpression.parse(game.fightCron());
    }

    @Transactional
    public void tick() {
        if (!fights.tryLock(LOCK_KEY)) {
            log.debug("Another tick holds the arena lock; skipping");
            return;
        }
        Instant now = clock.instant();
        boolean fighting = fights.lockLive()
                .map(live -> runDueExchanges(live, now))
                .filter(live -> live.status() == FightStatus.LIVE)
                .isPresent();

        Optional<Fight> announced = fights.lockAnnounced();
        if (announced.isPresent()) {
            Fight next = announced.get();
            if (!next.startsAt().isAfter(now)) {
                if (fighting) {
                    postpone(next);
                } else {
                    Fight started = start(next);
                    announce(nextSlot(started.startsAt()), started);
                }
            }
        } else if (!fighting) {
            announce(nextSlot(now), null); // bootstrap
        }
    }

    /** Runs every exchange due by {@code now}, settling the fight if one ends it. Returns the fight as it stands. */
    private Fight runDueExchanges(Fight fight, Instant now) {
        Stats red = gladiators.stats(fight.redId());
        Stats blue = gladiators.stats(fight.blueId());
        while (fight.status() == FightStatus.LIVE && !fight.nextExchangeAt().isAfter(now)) {
            int n = fight.exchangesDone() + 1;
            ExchangeResult exchange = Combat.exchange(fight.seed(), fight.id(), n, red, blue, fight.redHp(), fight.blueHp());
            exchanges.insert(fight.id(), exchange, fight.nextExchangeAt(), now);
            events.publishEvent(ExchangeRun.of(fight.id(), ExchangeView.of(exchange)));
            Optional<Outcome> outcome = Combat.ending(n, game.maxExchanges(), red, blue,
                    exchange.redHpAfter(), exchange.blueHpAfter());
            fight = outcome.isPresent()
                    ? fight.settle(exchange, outcome.get(), now)
                    : fight.afterExchange(exchange, dueAt(fight, n + 1));
            fights.save(fight);
        }
        if (fight.status() == FightStatus.SETTLED) {
            events.publishEvent(new FightSettled(fight.id(), fight.winner(), fight.endedBy()));
            log.info("Fight {} settled: {} by {} after {} exchanges", fight.id(), fight.winner(), fight.endedBy(), fight.exchangesDone());
        }
        return fight;
    }

    private Fight start(Fight fight) {
        Fight live = fights.save(fight.start(dueAt(fight, 1)));
        events.publishEvent(new FightStarted(live.id(),
                views.liveFighter(live.redId(), live.redHp()), views.liveFighter(live.blueId(), live.blueHp())));
        log.info("Fight {} started", live.id());
        return live;
    }

    /** The last fight is still live at this one's slot, so it moves to the following slot. */
    private void postpone(Fight fight) {
        Instant slot = nextSlot(fight.startsAt());
        fights.save(fight.postpone(slot));
        log.warn("Fight {} postponed from {} to {}: the last fight is still live", fight.id(), fight.startsAt(), slot);
    }

    /**
     * Announces a fight for {@code slot} (spec section 4, Pairing). The seed is drawn first, under
     * the id the fight will have, because the pairing's tie-break rolls with it.
     *
     * @param current the fight starting now, whose gladiators sit this one out; null at bootstrap
     */
    private void announce(Instant slot, Fight current) {
        long id = fights.nextId();
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        Set<Long> fighting = current == null ? Set.of() : Set.of(current.redId(), current.blueId());
        Map<Long, Instant> lastFought = fights.findLastFought().stream()
                .collect(Collectors.toMap(LastFought::gladiatorId, LastFought::startsAt));
        Pair pair = Pairing.pick(gladiators.roster().stream().map(Gladiator::id).toList(),
                lastFought, fighting, fighting, seed, id);

        Gladiator red = gladiators.gladiator(pair.redId());
        Gladiator blue = gladiators.gladiator(pair.blueId());
        fights.insertAnnounced(id, slot, red.id(), blue.id(), red.elo(), blue.elo(), seed,
                gladiators.stats(red.id()).hp(), gladiators.stats(blue.id()).hp());
        Fight fight = fights.findById(id).orElseThrow();
        events.publishEvent(new MatchupAnnounced(views.announced(fight)));
        log.info("Fight {} announced for {}: {} v {}", id, slot, red.slug(), blue.slug());
    }

    /** Exchange n is due at starts_at + n × exchange-interval. */
    private Instant dueAt(Fight fight, int n) {
        return fight.startsAt().plus(game.exchangeInterval().multipliedBy(n));
    }

    private Instant nextSlot(Instant after) {
        return fightCron.next(after.atZone(ZoneOffset.UTC)).toInstant();
    }
}
