package com.rdavies.arena.fight.records;

/** One gladiator in the roster list (GET /gladiators): public face, rating and W/L/D. */
public record GladiatorSummary(String slug, String name, String epithet, int elo, int wins, int losses, int draws) {}
