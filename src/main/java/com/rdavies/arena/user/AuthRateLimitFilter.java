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

/**
 * Refuses signup and login attempts over the limit before any password is looked at. It runs in the
 * security chain because Spring Security handles the login POST itself. The client IP is
 * getRemoteAddr(), which the forwarded-headers filter has already set from what Caddy reports.
 * Not a bean: Spring Boot would also register a Filter bean to run on every request.
 */
final class AuthRateLimitFilter extends OncePerRequestFilter {
    static final String TOO_MANY_ATTEMPTS = "Too many attempts from your connection. Please wait a while and try again.";

    /** The requests one limiter counts, and the page to show those it refuses. */
    record Rule(RequestMatcher requests, AttemptLimiter limiter, String page) {}

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
            if (rule.requests().matches(request)) {
                Optional<Duration> wait = rule.limiter().attempt(request.getRemoteAddr());
                if (wait.isPresent()) {
                    refuse(rule.page(), wait.get(), request, response);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void refuse(String page, Duration wait, HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(wait.plusNanos(999_999_999).toSeconds()));
        Map<String, Object> model = new HashMap<>();
        model.put("error", TOO_MANY_ATTEMPTS);
        model.put("signupBonus", signupBonus);
        model.put("email", request.getParameter("email"));
        model.put("displayName", request.getParameter("displayName"));
        try {
            View view = Objects.requireNonNull(views.resolveViewName(page, request.getLocale()), page);
            view.render(model, request, response);
        } catch (Exception e) {
            throw new ServletException("Could not render " + page, e);
        }
    }
}
