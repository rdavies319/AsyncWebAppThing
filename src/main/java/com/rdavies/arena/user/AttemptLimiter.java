package com.rdavies.arena.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Counts one kind of attempt per IP, in fixed windows that start at an IP's first attempt. The
 * counts live in memory, so a restart clears them. Every window here is the same length, so the
 * map's insertion order is also the order they end in: old counts are dropped from its head
 * without a scan or a scheduled job.
 *
 * <p>An active count is never evicted, because cycling through more addresses than the cap would
 * then escape the limit altogether. While the map is full, new IPs share one extra count instead.
 */
final class AttemptLimiter {
    private static final Logger log = LoggerFactory.getLogger(AttemptLimiter.class);
    private static final int IPV6_NETWORK_BYTES = 8;

    private final Clock clock;
    private final int maxAttempts;
    private final Duration length;
    private final int maxTrackedIps;
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private Window overflow;

    AttemptLimiter(Clock clock, int maxAttempts, Duration length, int maxTrackedIps) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.length = length;
        this.maxTrackedIps = maxTrackedIps;
    }

    /** Counts an attempt. Returns empty if it may go ahead, or how long the IP must wait if not. */
    synchronized Optional<Duration> attempt(String ip) {
        Instant now = clock.instant();
        dropEnded(now);
        String key = key(ip);
        Window window = unlessEnded(windows.get(key), now);
        if (window == null) {
            windows.remove(key); // dropEnded stops early if the clock stepped back
        }
        boolean full = window == null && windows.size() >= maxTrackedIps;
        if (full) {
            window = unlessEnded(overflow, now);
            if (window == null) {
                log.warn("Attempt limiter ({} per {}) is tracking its maximum of {} IPs: new IPs now share one count",
                        maxAttempts, length, maxTrackedIps);
            }
        }
        if (window != null && window.attempts >= maxAttempts) {
            return Optional.of(Duration.between(now, window.ends));
        }
        Window counted = window == null ? new Window(now.plus(length), 1) : new Window(window.ends, window.attempts + 1);
        if (full) {
            overflow = counted;
        } else {
            windows.put(key, counted); // an existing key keeps its place in the order
        }
        return Optional.empty();
    }

    synchronized int trackedIps() {
        return windows.size();
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

    /**
     * One host can hold a whole IPv6 /64, so that is what gets counted. Only an address with a
     * colon is parsed, because a colon-free string could send InetAddress off to do a DNS lookup.
     */
    private static String key(String ip) {
        if (!ip.contains(":")) {
            return ip;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address instanceof Inet6Address
                    ? HexFormat.of().formatHex(address.getAddress(), 0, IPV6_NETWORK_BYTES)
                    : address.getHostAddress(); // an IPv4 address written in IPv6 form
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    private record Window(Instant ends, int attempts) {}
}
