package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.events.RoundFought;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.fight.events.FightStarted;
import com.rdavies.arena.fight.events.MatchupAnnounced;
import com.rdavies.arena.fight.records.RoundResult;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.LastFought;
import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.fight.records.Pair;
import com.rdavies.arena.fight.records.RecentOpponent;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fight engine (spec section 7). Each tick is one transaction under the arena's advisory lock,
 * and every step is gated on fight status and rounds_done, so a repeated tick changes nothing.
 * Events are published inside the tick and reach listeners only after it commits.
 */
@Component
public class FightEngine {
    private static final Logger log = LoggerFactory.getLogger(FightEngine.class);
    /** The arena's advisory lock key ("ARENA" in ASCII). */
    static final long LOCK_KEY = 0x4152454E41L;

    private final FightRepository fights;
    private final FightRoundRepository rounds;
    private final FightService views;
    private final GladiatorService gladiators;
    private final ApplicationEventPublisher events;
    private final GameProperties game;
    private final Clock clock;
    private final CronExpression fightCron;
    private final SecureRandom random = new SecureRandom();

    FightEngine(FightRepository fights, FightRoundRepository rounds, FightService views,
                GladiatorService gladiators, ApplicationEventPublisher events, GameProperties game,
                Clock clock) {
        this.fights = fights;
        this.rounds = rounds;
        this.views = views;
        this.gladiators = gladiators;
        this.events = events;
        this.game = game;
        this.clock = clock;
        this.fightCron = CronExpression.parse(game.fightCron());
    }

    /** Runs one tick. Returns false if another tick held the arena lock, in which case nothing changed. */
    @Transactional
    public boolean tick() {
        if (!fights.tryLock(LOCK_KEY)) {
            log.debug("Another tick holds the arena lock; skipping");
            return false;
        }
        Instant now = clock.instant();
        boolean fighting = fights.lockLive()
                .map(live -> runDueRounds(live, now))
                .filter(live -> live.status() == FightStatus.LIVE)
                .isPresent();

        Optional<Fight> announced = fights.lockAnnounced();
        if (announced.isPresent()) {
            Fight next = announced.get();
            if (!next.startsAt().isAfter(now)) {
                if (fighting) {
                    postpone(next);
                } else if (next.startsAt().plus(game.roundInterval()).isBefore(now)) {
                    moveMissedStart(next, now);
                } else {
                    Fight started = start(next);
                    announce(nextSlot(started.startsAt()), started);
                }
            }
        } else if (!fighting) {
            announce(nextSlot(now), null); // bootstrap
        }
        return true;
    }

    /** Runs every round due by {@code now}, settling the fight if one ends it. Returns the fight as it stands. */
    private Fight runDueRounds(Fight fight, Instant now) {
        Stats red = gladiators.stats(fight.redId());
        Stats blue = gladiators.stats(fight.blueId());
        int firstRound = fight.roundsDone() + 1;
        Instant firstDue = fight.nextRoundAt();
        while (fight.status() == FightStatus.LIVE && !fight.nextRoundAt().isAfter(now)) {
            int n = fight.roundsDone() + 1;
            RoundResult round = Combat.round(fight.seed(), fight.id(), n, red, blue, fight.redHp(), fight.blueHp());
            rounds.insert(fight.id(), round, fight.nextRoundAt(), now);
            events.publishEvent(RoundFought.of(fight.id(), RoundView.of(round)));
            Optional<Outcome> outcome = Combat.ending(n, game.maxRounds(), red, blue,
                    round.redHpAfter(), round.blueHpAfter());
            fight = outcome.isPresent()
                    ? fight.settle(round, outcome.get(), now)
                    : fight.afterRound(round, dueAt(fight, n + 1));
            fights.save(fight);
        }
        int ran = fight.roundsDone() - firstRound + 1;
        Duration behind = ran == 0 ? Duration.ZERO : Duration.between(firstDue, now);
        if (ran > 1 || behind.compareTo(game.roundInterval()) > 0) { // the app was down: overdue rounds ran from the seed
            log.warn("Fight {} resumed {} behind: ran rounds {} to {} at once",
                    fight.id(), behind, firstRound, fight.roundsDone());
        }
        if (fight.status() == FightStatus.SETTLED) {
            settle(fight);
        }
        return fight;
    }

    /** Moves both ratings by the result, against the ratings stored at announcement (spec section 5, Elo update). */
    private void settle(Fight fight) {
        gladiators.moveElo(fight.redId(), fight.blueId(), Elo.redDelta(fight.redElo(), fight.blueElo(), fight.winner()));
        events.publishEvent(new FightSettled(fight.id(), fight.winner(), fight.endedBy(),
                gladiators.gladiator(fight.redId()).elo(), gladiators.gladiator(fight.blueId()).elo()));
        log.info("Fight {} settled: {} by {} after {} rounds", fight.id(), fight.winner(), fight.endedBy(), fight.roundsDone());
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
     * The app was down when this fight was due to start, by more than one round interval, so it moves
     * to the next slot after now rather than starting late. Betting reopens until the new cutoff, and
     * bets already placed stand.
     */
    private void moveMissedStart(Fight fight, Instant now) {
        Instant slot = nextSlot(now);
        fights.save(fight.postpone(slot));
        log.warn("Fight {} missed its start at {} by {}; moved to {}",
                fight.id(), fight.startsAt(), Duration.between(fight.startsAt(), now), slot);
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
        Map<Long, List<Long>> recentOpponents = fights.findRecentOpponents(Pairing.RECENT_OPPONENTS).stream()
                .collect(Collectors.groupingBy(RecentOpponent::gladiatorId,
                        Collectors.mapping(RecentOpponent::opponentId, Collectors.toList())));
        Pair pair = Pairing.pick(gladiators.roster().stream().map(Gladiator::id).toList(),
                lastFought, fighting, recentOpponents, seed, id);

        Gladiator red = gladiators.gladiator(pair.redId());
        Gladiator blue = gladiators.gladiator(pair.blueId());
        int redChance = Odds.redChanceBps(red.elo(), blue.elo());
        fights.insertAnnounced(id, slot, red.id(), blue.id(), red.elo(), blue.elo(),
                Odds.multiplierBps(redChance), Odds.multiplierBps(10_000 - redChance), seed,
                gladiators.stats(red.id()).hp(), gladiators.stats(blue.id()).hp());
        Fight fight = fights.findById(id).orElseThrow();
        events.publishEvent(new MatchupAnnounced(views.announced(fight, clock.instant())));
        log.info("Fight {} announced for {}: {} v {}", id, slot, red.slug(), blue.slug());
    }

    /** Round n is due at starts_at + n × round-interval. */
    private Instant dueAt(Fight fight, int n) {
        return fight.startsAt().plus(game.roundInterval().multipliedBy(n));
    }

    private Instant nextSlot(Instant after) {
        return fightCron.next(after.atZone(ZoneOffset.UTC)).toInstant();
    }
}
