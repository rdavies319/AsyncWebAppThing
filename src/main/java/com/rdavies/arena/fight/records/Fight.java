package com.rdavies.arena.fight.records;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.enums.Winner;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * A fight's row. Internal to the fight feature: it carries the seed, so only the engine and
 * FightService read it, and public views are built from it without the seed.
 *
 * @param redElo         red's rating at announcement
 * @param redMultiplierBps what a winning bet on red pays per point staked, in basis points, priced at announcement
 * @param seed           32 random bytes that fix the whole fight (internal, never published)
 * @param redHp          red's current HP
 * @param nextRoundAt when the next round is due; set only while live
 */
@Table("fights")
public record Fight(
    @Id Long id,
    Instant startsAt,
    FightStatus status,
    long redId,
    long blueId,
    int redElo,
    int blueElo,
    int redMultiplierBps,
    int blueMultiplierBps,
    byte[] seed,
    int redHp,
    int blueHp,
    int roundsDone,
    Instant nextRoundAt,
    Winner winner,
    EndedBy endedBy,
    Instant settledAt
) {
    public Fight start(Instant firstRoundAt) {
        return new Fight(id, startsAt, FightStatus.LIVE, redId, blueId, redElo, blueElo,
                redMultiplierBps, blueMultiplierBps, seed,
                redHp, blueHp, roundsDone, firstRoundAt, null, null, null);
    }

    /** After a round the fight carries on from. */
    public Fight afterRound(RoundResult round, Instant nextRoundAt) {
        return new Fight(id, startsAt, status, redId, blueId, redElo, blueElo,
                redMultiplierBps, blueMultiplierBps, seed,
                round.redHpAfter(), round.blueHpAfter(), round.n(), nextRoundAt, null, null, null);
    }

    /** After the round that ended it. */
    public Fight settle(RoundResult last, Outcome outcome, Instant now) {
        return new Fight(id, startsAt, FightStatus.SETTLED, redId, blueId, redElo, blueElo,
                redMultiplierBps, blueMultiplierBps, seed,
                last.redHpAfter(), last.blueHpAfter(), last.n(), null, outcome.winner(), outcome.endedBy(), now);
    }

    /** Moved to a later slot, while still open for betting. */
    public Fight postpone(Instant newStartsAt) {
        return new Fight(id, newStartsAt, status, redId, blueId, redElo, blueElo,
                redMultiplierBps, blueMultiplierBps, seed,
                redHp, blueHp, roundsDone, nextRoundAt, winner, endedBy, settledAt);
    }

    @Override
    public String toString() {
        return "Fight[id=" + id + ", status=" + status + ", startsAt=" + startsAt + "]"; // never the seed
    }
}
