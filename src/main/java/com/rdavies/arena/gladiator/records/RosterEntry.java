package com.rdavies.arena.gladiator.records;

/** One gladiator as the roster file describes them: public identity plus the hidden stat block. */
public record RosterEntry(String slug, String name, String epithet, String bio, Stats stats) {}
