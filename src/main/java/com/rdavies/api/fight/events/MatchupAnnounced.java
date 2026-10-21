package com.rdavies.api.fight.events;

import com.rdavies.api.fight.records.AnnouncedFight;

/** A fight was announced, opening it for betting (SSE matchup_announced). */
public record MatchupAnnounced(AnnouncedFight fight) {}
