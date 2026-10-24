package com.rdavies.arena;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link GameTest} under the dev profile, for the dev controls. Every class using it shares one
 * more Spring context. The cadence is pinned back to production over the dev profile's fast one, so
 * the engine still ticks only when a test says so.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@GameTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "game.engine.cron=-", "game.fight-cron=0 0 * * * *", "game.round-interval=1m", "game.max-rounds=30"})
public @interface DevGameTest {}
