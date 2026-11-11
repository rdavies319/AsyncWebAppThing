package com.rdavies.arena.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Counts one kind of attempt per key (a client IP or an account), in fixed windows that start at a
 * key's first attempt. The counts live in memory, so a restart clears them. Every window here is the
 * same length, so the map's insertion order is also the order they end in: old counts are dropped
 * from its head without a scan or a scheduled job.
 *
 * <p>An active count is never evicted, because cycling through more keys than the cap would then
 * escape the limit altogether. While the map is full, new keys share one extra count instead.
 */
final class AttemptLimiter {
    private static final Logger log = LoggerFactory.getLogger(AttemptLimiter.class);

    private final Clock clock;
    private final int maxAttempts;
    private final Duration length;
    private final int maxTracked;
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private Window overflow;

    AttemptLimiter(Clock clock, int maxAttempts, Duration length, int maxTracked) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.length = length;
        this.maxTracked = maxTracked;
    }

    /** Counts an attempt. Returns empty if it may go ahead, or how long the key must wait if not. */
    synchronized Optional<Duration> attempt(String key) {
        return count(key, true);
    }

    /** Like {@link #attempt}, but without counting one. */
    synchronized Optional<Duration> check(String key) {
        return count(key, false);
    }

    synchronized int trackedKeys() {
        return windows.size();
    }

    private Optional<Duration> count(String key, boolean counts) {
        Instant now = clock.instant();
        dropEnded(now);
        Window window = unlessEnded(windows.get(key), now);
        if (window == null) {
            windows.remove(key); // dropEnded stops early if the clock stepped back
        }
        boolean full = window == null && windows.size() >= maxTracked;
        if (full) {
            window = unlessEnded(overflow, now);
            if (window == null && counts) {
                log.warn("Attempt limiter ({} per {}) is tracking its maximum of {} keys: new keys now share one count",
                        maxAttempts, length, maxTracked);
            }
        }
        if (window != null && window.attempts >= maxAttempts) {
            return Optional.of(Duration.between(now, window.ends));
        }
        if (counts) {
            Window counted = window == null ? new Window(now.plus(length), 1) : new Window(window.ends, window.attempts + 1);
            if (full) {
                overflow = counted;
            } else {
                windows.put(key, counted); // an existing key keeps its place in the order
            }
        }
        return Optional.empty();
    }

    private void dropEnded(Instant now) {
        Iterator<Window> oldestFirst = windows.values().iterator();
        while (oldestFirst.hasNext() && !oldestFirst.next().ends.isAfter(now)) {
            oldestFirst.remove();
        }
    }

    private static Window unlessEnded(Window window, Instant now) {
        return window != null && window.ends.isAfter(now) ? window : null;
    }

    private record Window(Instant ends, int attempts) {}
}
