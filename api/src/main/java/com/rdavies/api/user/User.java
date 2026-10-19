package com.rdavies.api.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("users")
public record User(
    @Id Long userId,
    String username,
    @CreatedDate Instant createdAt
) {}
