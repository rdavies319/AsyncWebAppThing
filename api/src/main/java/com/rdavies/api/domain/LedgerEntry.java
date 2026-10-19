package com.rdavies.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("ledger")
public record LedgerEntry(
    @Id Long ledgerId,
    Long userId,
    Long roundId,
    Long amount,
    LedgerType type,
    Instant createdAt
) {
    public enum LedgerType {
        STAKE, PAYOUT, REFUND, TOPUP
    }
}
