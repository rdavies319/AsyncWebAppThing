package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.FightPage;
import com.rdavies.arena.fight.records.FightRecord;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.fight.records.SettledFight;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The public face of the fights: views built from the rows, never carrying the seed, stats or rolls. */
@Service
public class FightService {
    private static final String BETTING_CLOSED = "Betting on this fight has closed";

    private final FightRepository fights;
    private final FightRoundRepository rounds;
    private final GladiatorService gladiators;
    private final GameProperties game;
    private final Clock clock;

    FightService(FightRepository fights, FightRoundRepository rounds, GladiatorService gladiators,
                 GameProperties game, Clock clock) {
        this.fights = fights;
        this.rounds = rounds;
        this.gladiators = gladiators;
        this.game = game;
        this.clock = clock;
    }

    /** The live and announced fights, stamped with the server time. */
    @Transactional(readOnly = true)
    public CurrentFights current() {
        Instant now = clock.instant();
        return new CurrentFights(now,
                fights.findLive().map(this::live).orElse(null),
                fights.findAnnounced().map(fight -> announced(fight, now)).orElse(null));
    }

    /** The most recently settled fight; empty before the first one settles. */
    @Transactional(readOnly = true)
    public Optional<SettledFight> lastSettled() {
        return fights.findLastSettled().map(fight -> new SettledFight(fight.id(),
                liveFighter(fight.redId(), fight.redHp()), liveFighter(fight.blueId(), fight.blueHp()),
                fight.winner(), fight.endedBy(), fight.roundsDone()));
    }

    /** A settled fight's result. */
    public FightResult result(long fightId) {
        Fight fight = fights.findById(fightId).orElseThrow(() -> new IllegalStateException("No fight " + fightId));
        if (fight.winner() == null) {
            throw new IllegalStateException("Fight " + fightId + " hasn't settled");
        }
        return new FightResult(fight.id(), fight.winner(), fight.endedBy(), fight.roundsDone(), fight.redHp(), fight.blueHp());
    }

    /** The most recent settled fights, newest first. */
    @Transactional(readOnly = true)
    public List<FightRecord> history(int limit) {
        Map<Long, Gladiator> roster = roster();
        return fights.findByStatusOrderByStartsAtDesc(FightStatus.SETTLED, Limit.of(limit)).stream()
                .map(fight -> record(fight, roster))
                .toList();
    }

    /** One fight in any status, with what has happened so far; empty if there is no such fight. */
    @Transactional(readOnly = true)
    public Optional<FightPage> page(long fightId) {
        return fights.findById(fightId).map(fight -> new FightPage(record(fight, roster()), rounds(fightId)));
    }

    /** The public records of these fights, by id. */
    @Transactional(readOnly = true)
    public Map<Long, FightRecord> records(Collection<Long> fightIds) {
        Map<Long, Gladiator> roster = roster();
        Map<Long, FightRecord> byId = new HashMap<>();
        fights.findAllById(fightIds).forEach(fight -> byId.put(fight.id(), record(fight, roster)));
        return byId;
    }

    private Map<Long, Gladiator> roster() {
        return gladiators.roster().stream().collect(Collectors.toMap(Gladiator::id, g -> g));
    }

    private static FightRecord record(Fight fight, Map<Long, Gladiator> roster) {
        return new FightRecord(fight.id(), fight.startsAt(), fight.status(),
                side(roster.get(fight.redId()), fight.redElo(), fight.redMultiplierBps()),
                side(roster.get(fight.blueId()), fight.blueElo(), fight.blueMultiplierBps()),
                fight.winner(), fight.endedBy(), fight.roundsDone());
    }

    private static FightRecord.Side side(Gladiator g, int elo, int multiplierBps) {
        return new FightRecord.Side(g.slug(), g.name(), g.epithet(), elo, multiplierBps);
    }

    /** A fight's public round log so far, oldest first: no raw rolls. */
    @Transactional(readOnly = true)
    public List<RoundView> rounds(long fightId) {
        return rounds.findByFight(fightId).stream().map(e -> RoundView.of(e.result())).toList();
    }

    private LiveFight live(Fight fight) {
        return new LiveFight(fight.id(), fight.startsAt(),
                liveFighter(fight.redId(), fight.redHp()), liveFighter(fight.blueId(), fight.blueHp()),
                fight.roundsDone(), fight.nextRoundAt(),
                rounds(fight.id()));
    }

    /**
     * The fight a bet is placed on, share-locked until the caller's transaction ends: the engine
     * can't start it while a bet on it is being placed (spec section 7). Refuses with a conflict
     * once betting has closed (the cutoff has passed, or the fight has started) and as invalid if
     * there is no such fight.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AnnouncedFight lockForBetting(long fightId) {
        Instant now = clock.instant();
        return fights.findSharedByIdAndStatus(fightId, FightStatus.ANNOUNCED)
                .map(fight -> announced(fight, now))
                .filter(AnnouncedFight::bettingOpen)
                .orElseThrow(() -> fights.existsById(fightId)
                        ? GameRuleException.conflict(BETTING_CLOSED)
                        : GameRuleException.invalid("There is no such fight"));
    }

    LiveFight.Fighter liveFighter(long gladiatorId, int currentHp) {
        Gladiator g = gladiators.gladiator(gladiatorId);
        return new LiveFight.Fighter(g.slug(), g.name(), g.epithet(), gladiators.stats(gladiatorId).hp(), currentHp);
    }

    /** The announced fight as it stands at {@code now}: betting is open until game.cutoff before the start. */
    AnnouncedFight announced(Fight fight, Instant now) {
        Instant cutoffAt = fight.startsAt().minus(game.cutoff());
        return new AnnouncedFight(fight.id(), fight.startsAt(), cutoffAt, now.isBefore(cutoffAt),
                announcedFighter(fight.redId(), fight.redElo(), fight.redMultiplierBps()),
                announcedFighter(fight.blueId(), fight.blueElo(), fight.blueMultiplierBps()));
    }

    private AnnouncedFight.Fighter announcedFighter(long gladiatorId, int elo, int multiplierBps) {
        Gladiator g = gladiators.gladiator(gladiatorId);
        return new AnnouncedFight.Fighter(g.slug(), g.name(), g.epithet(), elo, multiplierBps,
                Odds.maxStake(game.maxStake(), game.maxPayout(), multiplierBps));
    }
}
