package com.rdavies.api.round;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * One minute slot. {@code rollValue} is rolled when the round opens and is the number players
 * predict against during it; the round's predictions settle against the next round's roll.
 */
@Table("rounds")
public record Round(
    @Id Long id,
    Instant startsAt,
    Integer rollValue,
    String seedCommitHash,
    String seed,
    RoundStatus status,
    Instant settledAt
) {
    /** Each round is rolled on its minute and settled when the next one is rolled. */
    public static final Duration LENGTH = Duration.ofMinutes(1);

    /** When the next round is rolled and this one settled, by the server clock. */
    public Instant rollTime() {
        return startsAt.plus(LENGTH);
    }

    /** The seed behind this round's roll, or null while it is still scheduled (not yet rolled). */
    public String revealedSeed() {
        return status == RoundStatus.SCHEDULED ? null : seed;
    }
}
