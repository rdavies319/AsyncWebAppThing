package com.rdavies.api.round;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
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
    /** The seed behind this round's roll, or null while it is still scheduled (not yet rolled). */
    public String revealedSeed() {
        return status == RoundStatus.SCHEDULED ? null : seed;
    }
}
