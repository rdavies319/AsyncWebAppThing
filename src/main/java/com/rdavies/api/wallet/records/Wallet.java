package com.rdavies.api.wallet.records;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Only ever changed by conditional UPDATEs in the wallet feature's WalletRepository, each paired with a ledger row. */
@Table("wallets")
public record Wallet(
    @Id Long userId,
    Long balance,
    /** UTC day of the last daily top-up, or null if never claimed. */
    LocalDate lastTopUpDate,
    Instant updatedAt
) {}
