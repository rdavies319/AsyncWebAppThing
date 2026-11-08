package com.rdavies.arena.user;

import com.rdavies.arena.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How many attempts one IP gets, and how long the limiter remembers it. */
class AttemptLimiterTests {
    private static final Instant START = Instant.parse("2026-06-01T12:00:00Z");
    private static final String IP = "203.0.113.1";

    private final MutableClock clock = new MutableClock();

    AttemptLimiterTests() {
        clock.set(START);
    }

    @Test
    void refusesAttemptsOverTheLimitUntilTheWindowHasPassed() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 2, Duration.ofMinutes(10), 100);

        assertEquals(Optional.empty(), limiter.attempt(IP));
        clock.set(START.plus(Duration.ofMinutes(4)));
        assertEquals(Optional.empty(), limiter.attempt(IP));
        assertEquals(Optional.of(Duration.ofMinutes(6)), limiter.attempt(IP));

        clock.set(START.plus(Duration.ofMinutes(10)));
        assertEquals(Optional.empty(), limiter.attempt(IP));
    }

    @Test
    void oneIpGoingOverTheLimitDoesNotAffectAnother() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 1, Duration.ofMinutes(10), 100);

        limiter.attempt(IP);
        assertTrue(limiter.attempt(IP).isPresent());
        assertEquals(Optional.empty(), limiter.attempt("203.0.113.2"));
    }

    @Test
    void countsAreDroppedOnceTheirWindowHasPassed() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 1, Duration.ofMinutes(10), 100);
        limiter.attempt("203.0.113.1");
        clock.set(START.plus(Duration.ofMinutes(5)));
        limiter.attempt("203.0.113.2");

        clock.set(START.plus(Duration.ofMinutes(11)));
        limiter.attempt("203.0.113.3");

        assertEquals(2, limiter.trackedIps());
    }

    @Test
    void aWindowEndsOnTimeEvenIfTheClockStepsBackBehindAnotherIp() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 1, Duration.ofMinutes(10), 100);
        clock.set(START.plus(Duration.ofHours(1)));
        limiter.attempt("203.0.113.1");
        clock.set(START);
        limiter.attempt("203.0.113.2");

        clock.set(START.plus(Duration.ofMinutes(10)));

        assertEquals(Optional.empty(), limiter.attempt("203.0.113.2"));
    }

    @Test
    void theOldestCountMakesWayWhenTooManyIpsAreTracked() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 1, Duration.ofMinutes(10), 2);
        limiter.attempt("203.0.113.1");
        limiter.attempt("203.0.113.2");

        limiter.attempt("203.0.113.3");

        assertEquals(2, limiter.trackedIps());
        assertTrue(limiter.attempt("203.0.113.3").isPresent(), "the newest is still counted");
        assertTrue(limiter.attempt("203.0.113.2").isPresent(), "so is the one before it");
        assertEquals(Optional.empty(), limiter.attempt("203.0.113.1"), "the oldest was forgotten");
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareACount() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 1, Duration.ofMinutes(10), 100);

        limiter.attempt("2001:db8:1:2:aaaa::1");

        assertTrue(limiter.attempt("2001:db8:1:2:bbbb::2").isPresent());
        assertEquals(Optional.empty(), limiter.attempt("2001:db8:1:3::1"));
    }

    @Test
    void anIpv4AddressMappedIntoIpv6CountsAsThatIpv4Address() {
        AttemptLimiter limiter = new AttemptLimiter(clock, 1, Duration.ofMinutes(10), 100);

        limiter.attempt("203.0.113.1");

        assertTrue(limiter.attempt("::ffff:203.0.113.1").isPresent());
    }
}
