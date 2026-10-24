package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.RollKind;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.Attack;
import com.rdavies.arena.fight.records.RoundResult;
import com.rdavies.arena.fight.records.Outcome;
import com.rdavies.arena.fight.records.SimulatedFight;
import com.rdavies.arena.gladiator.records.Stats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

/** The fight rules from spec section 4, as pure functions: no clock, no database, all integer maths. */
final class Combat {
    private Combat() {}

    /**
     * Round {@code n}: both gladiators attack at once, from the HP before the round, so a
     * gladiator knocked out in it still lands their blow.
     */
    static RoundResult round(byte[] seed, long fightId, int n, Stats red, Stats blue, int redHp, int blueHp) {
        Attack redAttack = attack(seed, fightId, n, Corner.RED, red, blue);
        Attack blueAttack = attack(seed, fightId, n, Corner.BLUE, blue, red);
        return new RoundResult(n, redAttack, blueAttack, redHp - blueAttack.damage(), blueHp - redAttack.damage());
    }

    /**
     * One attack from its rolls. The attack is dodged when {@code hitRoll} is at or below the
     * defender's agility; otherwise {@code dmgRoll} (1..attack range) is rolled and defence
     * taken off, leaving at least 1.
     */
    static Attack attack(Stats attacker, Stats defender, int hitRoll, IntSupplier dmgRoll) {
        if (hitRoll <= defender.agility()) {
            return new Attack(hitRoll, null, false, 0);
        }
        int dmg = dmgRoll.getAsInt();
        return new Attack(hitRoll, dmg, true, Math.max(1, attacker.attackMin() + dmg - 1 - defender.defence()));
    }

    /** How the fight ended after {@code roundsDone} rounds, or empty if it fights on. */
    static Optional<Outcome> ending(int roundsDone, int maxRounds, Stats red, Stats blue, int redHp, int blueHp) {
        boolean redDown = redHp <= 0;
        boolean blueDown = blueHp <= 0;
        if (redDown && blueDown) {
            return Optional.of(new Outcome(Winner.DRAW, EndedBy.DOUBLE_KO));
        }
        if (redDown || blueDown) {
            return Optional.of(new Outcome(redDown ? Winner.BLUE : Winner.RED, EndedBy.KO));
        }
        if (roundsDone < maxRounds) {
            return Optional.empty();
        }
        // HP left as a share of starting HP, in basis points with integer division
        int redShare = redHp * 10_000 / red.hp();
        int blueShare = blueHp * 10_000 / blue.hp();
        Winner winner = redShare == blueShare ? Winner.DRAW : redShare > blueShare ? Winner.RED : Winner.BLUE;
        return Optional.of(new Outcome(winner, EndedBy.DECISION));
    }

    /** Plays the whole fight from its seed. The same seed and stats always give the same fight. */
    static SimulatedFight simulate(byte[] seed, long fightId, Stats red, Stats blue, int maxRounds) {
        List<RoundResult> rounds = new ArrayList<>();
        int redHp = red.hp();
        int blueHp = blue.hp();
        for (int n = 1; ; n++) {
            RoundResult round = round(seed, fightId, n, red, blue, redHp, blueHp);
            rounds.add(round);
            redHp = round.redHpAfter();
            blueHp = round.blueHpAfter();
            Optional<Outcome> outcome = ending(n, maxRounds, red, blue, redHp, blueHp);
            if (outcome.isPresent()) {
                return new SimulatedFight(rounds, outcome.get());
            }
        }
    }

    private static Attack attack(byte[] seed, long fightId, int n, Corner corner, Stats attacker, Stats defender) {
        int hitRoll = FightRolls.roll(seed, fightId, n, corner, RollKind.HIT, 100);
        return attack(attacker, defender, hitRoll, () -> FightRolls.roll(seed, fightId, n, corner, RollKind.DMG,
                attacker.attackMax() - attacker.attackMin() + 1));
    }
}
