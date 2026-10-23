package com.rdavies.arena.fight;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Result;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.Career;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.GladiatorProfile;
import com.rdavies.arena.fight.records.GladiatorSummary;
import com.rdavies.arena.fight.records.HeadToHead;
import com.rdavies.arena.fight.records.RecentResult;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.gladiator.GladiatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A gladiator's public record (spec section 9), checked against a planted history: the first fight
 * a red KO win, the second a double KO between two others. Expected figures come from the public
 * round log, worked out here independently of the SQL.
 */
@GameTest
@Transactional
class CareerTests {
    @Autowired TestGame game;
    @Autowired CareerService careers;
    @Autowired FightService fightService;
    @Autowired GladiatorService gladiators;

    AnnouncedFight first;
    FightResult firstResult;
    AnnouncedFight second;
    FightResult secondResult;
    List<RoundView> firstRounds;

    @BeforeEach
    void plantHistory() {
        game.plantFight(Winner.RED, EndedBy.KO);
        first = fightService.current().announced();
        game.startFight(); // announces the second fight, between two others
        game.plantFight(Winner.DRAW, EndedBy.DOUBLE_KO);
        second = fightService.current().announced();
        firstResult = game.finishFight();
        secondResult = game.finishFight();
        firstRounds = fightService.rounds(first.fightId());
    }

    @Test
    void theWinnersRecord() {
        Career career = profile(first.red().slug()).record();

        int rounds = firstRounds.size();
        int dealt = firstRounds.stream().mapToInt(RoundView::redDamage).sum();
        int taken = firstRounds.stream().mapToInt(RoundView::blueDamage).sum();
        long dodged = firstRounds.stream().filter(r -> !r.blueHit()).count();
        assertEquals(new Career(1, 1, 0, 0, 10_000, 10_000, rounds * 100, rounds,
                dealt * 100 / rounds, taken * 100 / rounds, (int) (dodged * 10_000 / rounds)), career);
    }

    @Test
    void theLosersRecordHasNoKoRateWithoutAWin() {
        Career career = profile(first.blue().slug()).record();

        assertEquals(1, career.fights());
        assertEquals(1, career.losses());
        assertEquals(0, career.winRateBps());
        assertNull(career.koRateBps());
        assertEquals(firstRounds.stream().mapToInt(RoundView::redDamage).sum() * 100 / firstRounds.size(),
                career.avgDamageTakenX100());
    }

    @Test
    void aDrawCountsAsADraw() {
        Career career = profile(second.red().slug()).record();

        assertEquals(1, career.fights());
        assertEquals(0, career.wins());
        assertEquals(1, career.draws());
        assertEquals(0, career.winRateBps());
        assertEquals(secondResult.roundsDone() * 100, career.avgRoundsX100());
    }

    @Test
    void aGladiatorWhoHasNeverFoughtHasAnEmptyRecord() {
        GladiatorProfile rookie = careers.roster().stream()
                .filter(g -> g.wins() + g.losses() + g.draws() == 0)
                .findFirst().map(g -> profile(g.slug())).orElseThrow();

        assertEquals(new Career(0, 0, 0, 0, null, null, null, 0, null, null, null), rookie.record());
        assertEquals(List.of(), rookie.headToHead());
        assertEquals(List.of(), rookie.lastResults());
    }

    @Test
    void headToHeadAndLastResults() {
        GladiatorProfile winner = profile(first.red().slug());

        assertEquals(List.of(new HeadToHead(first.blue().slug(), first.blue().name(), 1, 0, 0)), winner.headToHead());
        assertEquals(List.of(new RecentResult(firstResult.fightId(), first.startsAt(), first.blue().slug(),
                first.blue().name(), Result.WON, EndedBy.KO)), winner.lastResults());
        assertEquals(List.of(new RecentResult(firstResult.fightId(), first.startsAt(), first.red().slug(),
                first.red().name(), Result.LOST, EndedBy.KO)), profile(first.blue().slug()).lastResults());
    }

    @Test
    void theProfileCarriesThePublicFace() {
        GladiatorProfile winner = profile(first.red().slug());

        assertEquals(first.red().name(), winner.name());
        assertEquals(first.red().epithet(), winner.epithet());
        assertEquals(gladiators.roster().stream().filter(g -> g.slug().equals(first.red().slug()))
                .findFirst().orElseThrow().elo(), winner.elo()); // moved by the win
        assertFalse(winner.bio().isBlank());
    }

    @Test
    void aLiveFightIsNotOnTheRecordYet() {
        AnnouncedFight third = fightService.current().announced();
        game.startFight();
        game.nextRound();

        assertEquals(0, profile(third.red().slug()).record().fights());
    }

    @Test
    void theRosterListsEveryGladiatorWithTheirRecord() {
        Map<String, GladiatorSummary> roster = careers.roster().stream()
                .collect(Collectors.toMap(GladiatorSummary::slug, Function.identity()));

        assertEquals(gladiators.roster().size(), roster.size());
        GladiatorSummary winner = roster.get(first.red().slug());
        assertEquals(List.of(1, 0, 0), List.of(winner.wins(), winner.losses(), winner.draws()));
        GladiatorSummary drawn = roster.get(second.blue().slug());
        assertEquals(List.of(0, 0, 1), List.of(drawn.wins(), drawn.losses(), drawn.draws()));
    }

    @Test
    void anUnknownSlugHasNoProfile() {
        assertTrue(careers.profile("nobody").isEmpty());
    }

    private GladiatorProfile profile(String slug) {
        return careers.profile(slug).orElseThrow();
    }
}
