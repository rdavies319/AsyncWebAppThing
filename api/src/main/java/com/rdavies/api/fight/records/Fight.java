package com.rdavies.api.fight.records;

import com.rdavies.api.fight.enums.EndedBy;
import com.rdavies.api.fight.enums.FightStatus;
import com.rdavies.api.fight.enums.Winner;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * A fight's row. Internal to the fight feature: it carries the seed, so only the engine and
 * FightService read it, and public views are built from it without the seed.
 *
 * @param redElo         red's rating at announcement
 * @param seed           32 random bytes that fix the whole fight (internal, never published)
 * @param redHp          red's current HP
 * @param nextExchangeAt when the next exchange is due; set only while live
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
    byte[] seed,
    int redHp,
    int blueHp,
    int exchangesDone,
    Instant nextExchangeAt,
    Winner winner,
    EndedBy endedBy,
    Instant settledAt
) {
    /** A new fight, open for betting until it starts. */
    public static Fight announce(Instant startsAt, long redId, long blueId, int redElo, int blueElo,
                                 byte[] seed, int redHp, int blueHp) {
        return new Fight(null, startsAt, FightStatus.ANNOUNCED, redId, blueId, redElo, blueElo, seed,
                redHp, blueHp, 0, null, null, null, null);
    }

    public Fight start(Instant firstExchangeAt) {
        return new Fight(id, startsAt, FightStatus.LIVE, redId, blueId, redElo, blueElo, seed,
                redHp, blueHp, exchangesDone, firstExchangeAt, null, null, null);
    }

    /** After an exchange the fight carries on from. */
    public Fight afterExchange(ExchangeResult exchange, Instant nextExchangeAt) {
        return new Fight(id, startsAt, status, redId, blueId, redElo, blueElo, seed,
                exchange.redHpAfter(), exchange.blueHpAfter(), exchange.n(), nextExchangeAt, null, null, null);
    }

    /** After the exchange that ended it. */
    public Fight settle(ExchangeResult last, Outcome outcome, Instant now) {
        return new Fight(id, startsAt, FightStatus.SETTLED, redId, blueId, redElo, blueElo, seed,
                last.redHpAfter(), last.blueHpAfter(), last.n(), null, outcome.winner(), outcome.endedBy(), now);
    }

    @Override
    public String toString() {
        return "Fight[id=" + id + ", status=" + status + ", startsAt=" + startsAt + "]"; // never the seed
    }
}
