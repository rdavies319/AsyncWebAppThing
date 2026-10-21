package com.rdavies.api.user.records;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("users")
public record User(
    @Id Long id,
    /** Stored lower-case. */
    String email,
    String displayName,
    String passwordHash,
    Instant createdAt
) {}
