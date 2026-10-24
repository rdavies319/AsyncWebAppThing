package com.rdavies.arena.gladiator.records;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * A gladiator's public face. Deliberately has no stat fields, so loading one never reads the
 * hidden stats; the fight feature gets those from GladiatorService.stats.
 */
@Table("gladiators")
public record Gladiator(
    @Id Long id,
    String slug,
    String name,
    String epithet,
    String bio,
    int elo,
    Instant createdAt
) {}
