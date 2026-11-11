package com.rdavies.arena.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Refuses signup and login attempts over the limit before any password is looked at. It runs in the
 * security chain because Spring Security handles the login POST itself. The client IP is
 * getRemoteAddr(), which the forwarded-headers filter has already set from what Caddy reports.
 * A rule may only check its limiter, leaving the counting to something that knows the outcome,
 * as {@link AccountLoginFailures} does for failed logins.
 * Not a bean: Spring Boot would also register a Filter bean to run on every request.
 */
final class AuthRateLimitFilter extends OncePerRequestFilter {
    static final String TOO_MANY_ATTEMPTS = "Too many attempts from your connection. Please wait a while and try again.";
    static final String TOO_MANY_FAILURES = "Too many failed logins for this account. Please wait a while and try again.";

    /**
     * The requests one limiter covers, the key it counts each under (null skips the request),
     * whether every request counts or is only checked, and the page and message for those it refuses.
     */
    record Rule(RequestMatcher requests, Function<HttpServletRequest, String> key, boolean countsEveryRequest,
                AttemptLimiter limiter, String page, String message) {
        /** Counts every matching request against its client IP. */
        static Rule perIp(RequestMatcher requests, AttemptLimiter limiter, String page) {
            return new Rule(requests, request -> ClientIp.key(request.getRemoteAddr()), true, limiter, page, TOO_MANY_ATTEMPTS);
        }
    }

    private final List<Rule> rules;
    private final ViewResolver views;
    private final long signupBonus;

    AuthRateLimitFilter(List<Rule> rules, ViewResolver views, long signupBonus) {
        this.rules = rules;
        this.views = views;
        this.signupBonus = signupBonus;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (Rule rule : rules) {
            String key = rule.requests().matches(request) ? rule.key().apply(request) : null;
            if (key != null) {
                Optional<Duration> wait = rule.countsEveryRequest() ? rule.limiter().attempt(key) : rule.limiter().check(key);
                if (wait.isPresent()) {
                    refuse(rule, wait.get(), request, response);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void refuse(Rule rule, Duration wait, HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(wait.plusNanos(999_999_999).toSeconds()));
        Map<String, Object> model = new HashMap<>();
        model.put("error", rule.message());
        model.put("signupBonus", signupBonus);
        model.put("email", request.getParameter("email"));
        model.put("displayName", request.getParameter("displayName"));
        try {
            View view = Objects.requireNonNull(views.resolveViewName(rule.page(), request.getLocale()), rule.page());
            view.render(model, request, response);
        } catch (Exception e) {
            throw new ServletException("Could not render " + rule.page(), e);
        }
    }
}
