package com.rdavies.api.wallet;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("wallets")
public record Wallet(
    @Id Long userId,
    Long balance,
    @CreatedDate Instant lastTopUp
) {}
