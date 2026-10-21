package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.Attack;
import com.rdavies.arena.fight.records.ExchangeResult;
import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.fight.records.SimulatedFight;
import com.rdavies.arena.gladiator.records.Stats;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** One exchange, the end-of-fight rules and a whole fight, from spec section 4. */
class CombatTests {
    private static final byte[] SEED = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    private static final int CAP = 30;

    // --- One attack, from given rolls

    @Test
    void anAttackIsDodgedAtOrBelowTheDefendersAgility() {
        Stats attacker = stats(100, 5, 9, 0, 10);
        Stats defender = stats(100, 5, 9, 0, 20);

        assertEquals(new Attack(20, null, false, 0), Combat.attack(attacker, defender, 20, () -> fail("no damage roll on a dodge")));
        assertEquals(new Attack(21, 3, true, 7), Combat.attack(attacker, defender, 21, () -> 3)); // 5 + 3 - 1 - 0
    }

    @Test
    void defenceReducesEveryHitButAHitAlwaysDoesAtLeastOne() {
        Stats attacker = stats(100, 4, 22, 0, 10);

        assertEquals(16, Combat.attack(attacker, stats(100, 4, 22, 6, 0), 50, () -> 19).damage()); // 4 + 19 - 1 - 6
        assertEquals(1, Combat.attack(attacker, stats(100, 4, 22, 6, 0), 50, () -> 1).damage()); // 4 + 1 - 1 - 6 < 1
    }

    // --- One exchange, from the seed. Rolls worked out with openssl (see DiceTests).

    @Test
    void bothAttacksResolveFromTheStateBeforeTheExchange() {
        Stats red = stats(100, 10, 18, 2, 20);
        Stats blue = stats(90, 4, 22, 3, 25);

        ExchangeResult result = Combat.exchange(SEED, 1, 1, red, blue, 100, 90);

        // red: hit roll 70 > blue's agility 25, dmg roll 4 of 1..9: 10 + 4 - 1 - 3 = 10
        assertEquals(new Attack(70, 4, true, 10), result.red());
        // blue: hit roll 26 > red's agility 20, dmg roll 1 of 1..19: max(1, 4 + 1 - 1 - 2) = 2
        assertEquals(new Attack(26, 1, true, 2), result.blue());
        assertEquals(98, result.redHpAfter());
        assertEquals(80, result.blueHpAfter());
    }

    @Test
    void aKnockedOutGladiatorStillLandsTheirBlow() {
        Stats red = stats(100, 10, 18, 2, 20);
        Stats blue = stats(90, 4, 22, 3, 25);

        ExchangeResult result = Combat.exchange(SEED, 1, 1, red, blue, 2, 10); // both fall in the same exchange

        assertEquals(0, result.redHpAfter());
        assertEquals(0, result.blueHpAfter());
    }

    @Test
    void theHitRollDodgesAtExactlyTheDefendersAgility() {
        Stats red = stats(100, 10, 18, 2, 26); // blue's hit roll is 26
        ExchangeResult result = Combat.exchange(SEED, 1, 1, red, stats(90, 4, 22, 3, 25), 100, 90);

        assertEquals(new Attack(26, null, false, 0), result.blue());
        assertEquals(100, result.redHpAfter());
    }

    // --- Endings

    @Test
    void oneGladiatorDownIsAKnockout() {
        Stats any = stats(100, 5, 9, 0, 10);
        assertEquals(Optional.of(new Outcome(Winner.BLUE, EndedBy.KO)), Combat.ending(4, CAP, any, any, 0, 12));
        assertEquals(Optional.of(new Outcome(Winner.RED, EndedBy.KO)), Combat.ending(4, CAP, any, any, 5, -3));
    }

    @Test
    void bothDownIsADraw() {
        Stats any = stats(100, 5, 9, 0, 10);
        assertEquals(Optional.of(new Outcome(Winner.DRAW, EndedBy.DOUBLE_KO)), Combat.ending(7, CAP, any, any, -1, 0));
    }

    @Test
    void bothStandingBeforeTheCapFightsOn() {
        Stats any = stats(100, 5, 9, 0, 10);
        assertEquals(Optional.empty(), Combat.ending(29, CAP, any, any, 1, 1));
    }

    @Test
    void atTheCapTheHigherShareOfHpWins() {
        // red keeps 60/120 = 5000, blue keeps 51/100 = 5100: blue has less HP left but the larger share
        assertEquals(Optional.of(new Outcome(Winner.BLUE, EndedBy.CAP)),
                Combat.ending(CAP, CAP, stats(120, 5, 9, 0, 10), stats(100, 5, 9, 0, 10), 60, 51));
    }

    @Test
    void atTheCapEqualSharesAreADraw() {
        // 50/100 and 41/82 are both 5000 by integer division
        assertEquals(Optional.of(new Outcome(Winner.DRAW, EndedBy.CAP)),
                Combat.ending(CAP, CAP, stats(100, 5, 9, 0, 10), stats(82, 5, 9, 0, 10), 50, 41));
        // 33/100 = 3300 and 33/99 = 3333, so not equal
        assertEquals(Optional.of(new Outcome(Winner.BLUE, EndedBy.CAP)),
                Combat.ending(CAP, CAP, stats(100, 5, 9, 0, 10), stats(99, 5, 9, 0, 10), 33, 33));
    }

    @Test
    void aKnockoutInTheLastExchangeIsStillAKnockout() {
        Stats any = stats(100, 5, 9, 0, 10);
        assertEquals(Optional.of(new Outcome(Winner.RED, EndedBy.KO)), Combat.ending(CAP, CAP, any, any, 40, 0));
    }

    // --- A whole fight

    @Test
    void theSameSeedAlwaysGivesTheSameFight() {
        Stats red = stats(110, 6, 16, 2, 15);
        Stats blue = stats(100, 8, 14, 1, 20);

        SimulatedFight first = Combat.simulate(SEED, 9, red, blue, CAP);

        assertEquals(first, Combat.simulate(SEED, 9, red, blue, CAP));
        assertNotEquals(first, Combat.simulate(SEED, 10, red, blue, CAP)); // the fight id is part of every roll
    }

    @Test
    void aSimulatedFightRunsExchangeByExchangeUntilItEnds() {
        Stats red = stats(110, 6, 16, 2, 15);
        Stats blue = stats(100, 8, 14, 1, 20);

        SimulatedFight fight = Combat.simulate(SEED, 9, red, blue, CAP);

        int redHp = red.hp();
        int blueHp = blue.hp();
        for (int i = 0; i < fight.exchanges().size(); i++) {
            ExchangeResult exchange = fight.exchanges().get(i);
            assertEquals(i + 1, exchange.n());
            assertEquals(Combat.exchange(SEED, 9, i + 1, red, blue, redHp, blueHp), exchange);
            redHp = exchange.redHpAfter();
            blueHp = exchange.blueHpAfter();
            boolean last = i == fight.exchanges().size() - 1;
            assertEquals(last, Combat.ending(i + 1, CAP, red, blue, redHp, blueHp).isPresent());
        }
        assertEquals(Combat.ending(fight.exchanges().size(), CAP, red, blue, redHp, blueHp).orElseThrow(), fight.outcome());
    }

    @Test
    void evenUntouchableGladiatorsStopAtTheCap() {
        Stats untouchable = stats(100, 5, 9, 0, 100); // every attack is dodged

        SimulatedFight fight = Combat.simulate(SEED, 3, untouchable, untouchable, CAP);

        assertEquals(CAP, fight.exchanges().size());
        assertEquals(new Outcome(Winner.DRAW, EndedBy.CAP), fight.outcome());
    }

    private static Stats stats(int hp, int attackMin, int attackMax, int defence, int agility) {
        return new Stats(hp, attackMin, attackMax, defence, agility);
    }
}
