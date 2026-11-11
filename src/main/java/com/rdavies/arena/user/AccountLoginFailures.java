package com.rdavies.arena.user;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * Counts a wrong password against its account, whichever IP sent it, so guesses spread over many
 * IPs still reach a limit. {@link AuthRateLimitFilter} checks the same limiter before the next
 * login. Only real accounts are counted: unknown emails can't be broken into, and leaving them out
 * means the number of players bounds the limiter's memory. Then it redirects as Spring Security's
 * default handler would.
 */
final class AccountLoginFailures implements AuthenticationFailureHandler {
    private final AuthenticationFailureHandler redirect;
    private final AttemptLimiter limiter;
    private final UserService users;

    AccountLoginFailures(String failureUrl, AttemptLimiter limiter, UserService users) {
        this.redirect = new SimpleUrlAuthenticationFailureHandler(failureUrl);
        this.limiter = limiter;
        this.users = users;
    }

    /** The key an account's failures are counted under, or null if the request names none. */
    static String key(HttpServletRequest request) {
        String email = request.getParameter("email");
        return email == null ? null : UserService.normaliseEmail(email);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String key = key(request);
        // Spring Security reports an unknown email as bad credentials too, hence the lookup
        if (exception instanceof BadCredentialsException && key != null && users.hasAccount(key)) {
            limiter.attempt(key);
        }
        redirect.onAuthenticationFailure(request, response, exception);
    }
}
