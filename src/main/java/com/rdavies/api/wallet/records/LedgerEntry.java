package com.rdavies.api.wallet.records;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

/** One append-only balance change. {@code refId} is the bet id for STAKE, PAYOUT and REFUND. */
@Table("ledger")
public record LedgerEntry(
    @Id Long id,
    Long userId,
    Long delta,
    Reason reason,
    Long refId,
    Instant createdAt
) {
    public enum Reason {
        SIGNUP_BONUS, STAKE, PAYOUT, REFUND, TOPUP
    }
}
