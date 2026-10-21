package com.rdavies.arena;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Replaces the app's Clock in tests, which set {@code now} and drive the engine by calling tick(). */
public class MutableClock extends Clock {
    Instant now = Instant.EPOCH;

    /** Sets the time, for tests outside this package. */
    public void set(Instant when) {
        now = when;
    }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }
}
