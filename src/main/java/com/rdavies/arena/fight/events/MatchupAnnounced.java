package com.rdavies.arena.fight.events;

import com.rdavies.arena.fight.records.AnnouncedFight;

/** A fight was announced, opening it for betting (SSE matchup_announced). */
public record MatchupAnnounced(AnnouncedFight fight) {}
