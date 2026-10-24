package com.rdavies.arena.fight;

import com.rdavies.arena.fight.records.GladiatorProfile;
import com.rdavies.arena.fight.records.GladiatorSummary;
import com.rdavies.arena.fight.records.HeadToHead;
import com.rdavies.arena.fight.records.RecentResult;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.gladiator.records.Gladiator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The gladiators' public records (spec section 9): what a player studies to beat the line. Built from
 * public fight history and the gladiators' public faces only, never their stats.
 */
@Service
public class CareerService {
    /** How many recent results a profile shows. */
    static final int LAST_RESULTS = 5;

    private final CareerRepository careers;
    private final GladiatorService gladiators;

    CareerService(CareerRepository careers, GladiatorService gladiators) {
        this.careers = careers;
        this.gladiators = gladiators;
    }

    /** Every gladiator with their W/L/D, in roster order. */
    @Transactional(readOnly = true)
    public List<GladiatorSummary> roster() {
        Map<Long, CareerRepository.HeadToHeadRow> records = careers.findRecords().stream()
                .collect(Collectors.toMap(CareerRepository.HeadToHeadRow::opponent, Function.identity()));
        return gladiators.roster().stream().map(g -> {
            CareerRepository.HeadToHeadRow r = records.getOrDefault(g.id(), new CareerRepository.HeadToHeadRow(g.id(), 0, 0, 0));
            return new GladiatorSummary(g.slug(), g.name(), g.epithet(), g.elo(), r.wins(), r.losses(), r.draws());
        }).toList();
    }

    /** The gladiator's public page; empty if no gladiator has this slug. */
    @Transactional(readOnly = true)
    public Optional<GladiatorProfile> profile(String slug) {
        Map<Long, Gladiator> roster = gladiators.roster().stream()
                .collect(Collectors.toMap(Gladiator::id, Function.identity()));
        return roster.values().stream().filter(g -> g.slug().equals(slug)).findFirst().map(g -> {
            List<HeadToHead> headToHead = careers.findHeadToHead(g.id()).stream().map(row -> {
                Gladiator opponent = roster.get(row.opponent());
                return new HeadToHead(opponent.slug(), opponent.name(), row.wins(), row.losses(), row.draws());
            }).toList();
            List<RecentResult> lastResults = careers.findRecent(g.id(), LAST_RESULTS).stream().map(row -> {
                Gladiator opponent = roster.get(row.opponent());
                return new RecentResult(row.fightId(), row.startsAt(), opponent.slug(), opponent.name(),
                        row.result(), row.endedBy());
            }).toList();
            return new GladiatorProfile(g.slug(), g.name(), g.epithet(), g.bio(), g.elo(),
                    careers.findCareer(g.id()), headToHead, lastResults);
        });
    }
}
