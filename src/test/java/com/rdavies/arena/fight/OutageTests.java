package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.fight.records.SimulatedFight;
import com.rdavies.arena.gladiator.GladiatorService;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.TestWallets;
import com.rdavies.arena.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Downtime (spec section 7): a fight interrupted by an outage resumes from its seed and ends exactly
 * as it would have, and a fight whose start was missed moves to the next slot. Nothing is voided.
 * An outage is the test clock jumping forward between ticks.
 */
@GameTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class OutageTests {
    private static final Instant ONE_AM = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant TWO_AM = Instant.parse("2026-01-01T02:00:00Z");

    @Autowired TestGame game;
    @Autowired TestFights testFights;
    @Autowired FightService fightService;
    @Autowired FightRepository fights;
    @Autowired GladiatorService gladiators;
    @Autowired GameProperties properties;
    @Autowired BetService betService;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;
    @Autowired MutableClock clock;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aMidFightOutageReplaysEveryOverdueRoundFromTheSeed() {
        plantLongFight();
        LiveFight live = game.startFight();
        game.nextRound();
        game.nextRound();

        // Down from 01:02 to 01:08:30: rounds 3 to 8 are overdue
        LiveFight resumed = game.tickAt(ONE_AM.plusSeconds(8 * 60 + 30)).live();

        SimulatedFight uninterrupted = simulate(live.fightId());
        assertNotNull(resumed, "still live after catching up");
        assertEquals(8, resumed.roundsDone());
        assertEquals(uninterrupted.rounds().get(7).redHpAfter(), resumed.red().currentHp());
        assertEquals(uninterrupted.rounds().get(7).blueHpAfter(), resumed.blue().currentHp());
        assertEquals(ONE_AM.plusSeconds(9 * 60), resumed.nextRoundAt()); // the next interval after now

        FightResult result = game.finishFight();
        assertEquals(uninterrupted.outcome().winner(), result.winner());
        assertEquals(uninterrupted.outcome().endedBy(), result.endedBy());
        assertEquals(uninterrupted.rounds().stream().map(RoundView::of).toList(),
                fightService.rounds(live.fightId()));
    }

    @Test
    void aFightThatWouldHaveEndedDuringTheOutageSettlesOnTheFirstTick() {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        LiveFight live = game.startFight();

        CurrentFights after = game.tickAt(ONE_AM.plusSeconds(45 * 60)); // past the round cap

        assertNull(after.live());
        FightResult result = fightService.result(live.fightId());
        SimulatedFight uninterrupted = simulate(live.fightId());
        assertEquals(Winner.BLUE, result.winner());
        assertEquals(EndedBy.KO, result.endedBy());
        assertEquals(uninterrupted.rounds().size(), result.roundsDone());
        assertEquals(TWO_AM, after.announced().startsAt()); // the next fight is still on time
    }

    @Test
    void aResumedFightLogsTheGapAtWarn(CapturedOutput output) {
        plantLongFight();
        LiveFight live = game.startFight();

        game.tickAt(ONE_AM.plusSeconds(10 * 60 + 15));

        assertTrue(output.getOut().contains("WARN"), output.getOut());
        assertTrue(output.getOut().contains("Fight " + live.fightId() + " resumed PT9M15S behind: ran rounds 1 to 10 at once"),
                output.getOut());
    }

    @Test
    void anOutageIsLoggedEvenWhenTheFirstOverdueRoundEndsTheFight(CapturedOutput output) {
        game.plantFight(Winner.RED, EndedBy.KO);
        LiveFight live = game.startFight();
        int last = simulate(live.fightId()).rounds().size();
        while (fightService.current().live().roundsDone() < last - 1) {
            game.nextRound();
        }
        Instant due = fightService.current().live().nextRoundAt();

        game.tickAt(due.plusSeconds(10 * 60)); // only the knockout round was left

        assertNull(fightService.current().live());
        assertTrue(output.getOut().contains("Fight " + live.fightId() + " resumed PT10M behind: ran rounds "
                + last + " to " + last + " at once"), output.getOut());
    }

    @Test
    void repeatedTicksAfterCatchingUpChangeNothing() {
        plantLongFight();
        LiveFight live = game.startFight();
        Instant back = ONE_AM.plusSeconds(6 * 60 + 30);
        LiveFight caughtUp = game.tickAt(back).live();

        LiveFight again = game.tickAt(back).live();

        assertEquals(caughtUp, again);
        assertEquals(FightStatus.LIVE, fights.findById(live.fightId()).orElseThrow().status());
    }

    @Test
    void aStartMissedByMoreThanOneRoundMovesToTheNextSlot(CapturedOutput output) {
        AnnouncedFight announced = game.boot().announced();

        CurrentFights after = game.tickAt(ONE_AM.plusSeconds(5 * 60)); // down over the start

        assertNull(after.live());
        assertEquals(announced.fightId(), after.announced().fightId()); // the same fight, not voided
        assertEquals(TWO_AM, after.announced().startsAt());
        assertEquals(TWO_AM.minus(properties.cutoff()), after.announced().cutoffAt());
        assertEquals(announced.red(), after.announced().red());
        assertTrue(output.getOut().contains("Fight " + announced.fightId() + " missed its start at "
                + ONE_AM + " by PT5M; moved to " + TWO_AM), output.getOut());

        assertEquals(announced.fightId(), game.tickAt(TWO_AM).live().fightId());
    }

    @Test
    void aStartLessThanOneRoundLateStillStarts() {
        AnnouncedFight announced = game.boot().announced();

        LiveFight live = game.tickAt(ONE_AM.plus(properties.roundInterval())).live();

        assertEquals(announced.fightId(), live.fightId());
        assertEquals(ONE_AM, fights.findById(live.fightId()).orElseThrow().startsAt());
    }

    @Test
    void betsStandWhenAStartMovesAndBettingReopensUntilTheNewCutoff() {
        long early = TestPlayers.signUp(userService, "early_bird").id();
        long late = TestPlayers.signUp(userService, "late_comer").id();
        AnnouncedFight announced = game.boot().announced();
        clock.set(Instant.parse("2026-01-01T00:30:00Z"));
        betService.place(early, announced.fightId(), Corner.RED, 100L);

        game.tickAt(ONE_AM.plusSeconds(5 * 60));
        clock.set(Instant.parse("2026-01-01T01:30:00Z"));
        betService.place(late, announced.fightId(), Corner.BLUE, 50L);
        clock.set(Instant.parse("2026-01-01T01:59:50Z"));
        assertThrows(GameRuleException.class, () -> betService.place(late, announced.fightId(), Corner.RED, 10L));

        assertEquals(BetStatus.PENDING, betService.bet(early, announced.fightId()).orElseThrow().status());
        assertEquals(900, walletService.getWallet(early).balance());
        assertEquals(950, walletService.getWallet(late).balance());
    }

    /** A fight of at least 12 rounds, long enough to outlast these outages. */
    private void plantLongFight() {
        game.boot();
        testFights.plantLasting(12);
    }

    private SimulatedFight simulate(long fightId) {
        Fight fight = fights.findById(fightId).orElseThrow();
        return Combat.simulate(fight.seed(), fight.id(), gladiators.stats(fight.redId()), gladiators.stats(fight.blueId()),
                properties.maxRounds());
    }
}
