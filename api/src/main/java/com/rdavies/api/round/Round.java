package com.rdavies.api.round;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("rounds")
public record Round(
    @Id Long roundId,
    int previousRoll,
    Integer currentRoll,
    RoundStatus status,
    Instant startTime,
    Instant endTime,
    Instant createdAt,
    String seedCommitHash,
    String seed
) {
    /** The seed behind this round's roll, or null while the round is still open. */
    public String revealedSeed() {
        return status == RoundStatus.OPEN ? null : seed;
    }
}
