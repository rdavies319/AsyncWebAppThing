package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.fight.records.SettledFight;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/** The public face of the fights: views built from the rows, never carrying the seed, stats or rolls. */
@Service
public class FightService {
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
        return new CurrentFights(clock.instant(),
                fights.findLive().map(this::live).orElse(null),
                fights.findAnnounced().map(this::announced).orElse(null));
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

    private LiveFight live(Fight fight) {
        return new LiveFight(fight.id(), fight.startsAt(),
                liveFighter(fight.redId(), fight.redHp()), liveFighter(fight.blueId(), fight.blueHp()),
                fight.roundsDone(), fight.nextRoundAt(),
                rounds.findByFight(fight.id()).stream().map(e -> RoundView.of(e.result())).toList());
    }

    public boolean exists(long fightId) {
        return fights.existsById(fightId);
    }

    /**
     * The fight a bet is placed on, if it is still announced, share-locked until the caller's
     * transaction ends: the engine can't start it while a bet on it is being placed (spec section 7).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AnnouncedFight> lockForBetting(long fightId) {
        return fights.findSharedByIdAndStatus(fightId, FightStatus.ANNOUNCED).map(this::announced);
    }

    LiveFight.Fighter liveFighter(long gladiatorId, int currentHp) {
        Gladiator g = gladiators.gladiator(gladiatorId);
        return new LiveFight.Fighter(g.slug(), g.name(), g.epithet(), gladiators.stats(gladiatorId).hp(), currentHp);
    }

    AnnouncedFight announced(Fight fight) {
        return new AnnouncedFight(fight.id(), fight.startsAt(), fight.startsAt().minus(game.cutoff()),
                announcedFighter(fight.redId(), fight.redElo(), fight.redMultiplierBps()),
                announcedFighter(fight.blueId(), fight.blueElo(), fight.blueMultiplierBps()));
    }

    private AnnouncedFight.Fighter announcedFighter(long gladiatorId, int elo, int multiplierBps) {
        Gladiator g = gladiators.gladiator(gladiatorId);
        return new AnnouncedFight.Fighter(g.slug(), g.name(), g.epithet(), elo, multiplierBps,
                Odds.maxStake(game.maxStake(), game.maxPayout(), multiplierBps));
    }
}
