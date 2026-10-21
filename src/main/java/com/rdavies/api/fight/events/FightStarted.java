package com.rdavies.api.fight.events;

import com.rdavies.api.fight.records.LiveFight;

/** A fight went live (SSE fight_started): the corners with their starting HP. */
public record FightStarted(long fightId, LiveFight.Fighter red, LiveFight.Fighter blue) {}
