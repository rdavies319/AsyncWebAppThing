package com.rdavies.arena;

import com.rdavies.arena.user.UserRepository;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Signup and login limits through the real security chain, at the production defaults. The counts
 * outlive a test and the context is shared, so each test uses IP addresses of its own.
 */
@GameTest
@Transactional
class AuthRateLimitTests {
    private static final Instant START = Instant.parse("2026-06-01T12:00:00Z");
    private static final String REFUSED = "Too many attempts";
    private static final String ACCOUNT_REFUSED = "Too many failed logins for this account";

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired GameProperties game;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired TestWallets wallets;

    private Instant timeBefore;

    @BeforeEach
    void startTheClock() {
        timeBefore = clock.instant();
        clock.set(START);
    }

    @AfterEach
    void putTheClockBack() {
        clock.set(timeBefore);
    }

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void loginsOverTheLimitAreRefusedEvenWithTheRightPassword() throws Exception {
        String ip = "198.51.100.1";
        TestPlayers.signUp(userService, "ratelimit_ann");
        useUpLogins(ip);

        login(ip, "ratelimit_ann@example.invalid", TestPlayers.PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(content().string(containsString(REFUSED)))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(unauthenticated());
    }

    @Test
    void loginsAreAllowedAgainOnceTheWindowHasPassed() throws Exception {
        String ip = "198.51.100.2";
        TestPlayers.signUp(userService, "ratelimit_ben");
        useUpLogins(ip);

        clock.set(START.plus(game.authLimits().loginWindow()));

        login(ip, "ratelimit_ben@example.invalid", TestPlayers.PASSWORD)
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("ratelimit_ben@example.invalid"));
    }

    @Test
    void oneIpOverTheLimitDoesNotAffectAnother() throws Exception {
        useUpLogins("198.51.100.3");

        login("198.51.100.4", "nobody@example.invalid", "wrong password")
                .andExpect(redirectedUrl("/auth/login?error"));
    }

    @Test
    void signupsOverTheLimitAreRefusedAndCreateNoAccount() throws Exception {
        String ip = "198.51.100.5";
        useUpSignups(ip);

        signup(ip, "ratelimit_cat@example.invalid", "ratelimit_cat")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(content().string(containsString(REFUSED)))
                .andExpect(content().string(containsString("1,000"))) // the page's signup bonus still renders
                .andExpect(content().string(containsString("value=\"ratelimit_cat@example.invalid\""))); // keeps the input

        assertFalse(userRepository.existsByDisplayName("ratelimit_cat"));
    }

    @Test
    void signupsAreAllowedAgainOnceTheWindowHasPassed() throws Exception {
        String ip = "198.51.100.6";
        useUpSignups(ip);

        clock.set(START.plus(game.authLimits().signupWindow()));

        signup(ip, "ratelimit_dan@example.invalid", "ratelimit_dan")
                .andExpect(redirectedUrl("/auth/login?registered"));
    }

    @Test
    void signupAndLoginHaveSeparateCounts() throws Exception {
        useUpLogins("198.51.100.7");
        signup("198.51.100.7", "ratelimit_eve@example.invalid", "ratelimit_eve")
                .andExpect(redirectedUrl("/auth/login?registered"));

        useUpSignups("198.51.100.8");
        login("198.51.100.8", "nobody@example.invalid", "wrong password")
                .andExpect(redirectedUrl("/auth/login?error"));
    }

    @Test
    void anIpOverTheLimitCanStillWatchAndOpenTheForms() throws Exception {
        String ip = "198.51.100.9";
        useUpLogins(ip);
        useUpSignups(ip);

        for (String page : new String[] {"/", "/fragments/board", "/leaderboard", "/auth/login", "/auth/signup"}) {
            mockMvc.perform(fromIp(get(page), ip)).andExpect(status().isOk());
        }
    }

    @Test
    void theCountFollowsTheClientIpFromTheProxy() throws Exception {
        String proxy = "172.18.0.2";
        for (int i = 0; i < game.authLimits().signupMaxAttempts(); i++) {
            attemptSignup(forwardedFor("198.51.100.10", proxy)).andExpect(status().isOk());
        }

        attemptSignup(forwardedFor("198.51.100.10", proxy)).andExpect(status().isTooManyRequests());
        attemptSignup(forwardedFor("198.51.100.11", proxy)).andExpect(status().isOk());
    }

    @Test
    void anAccountWithTooManyFailedLoginsIsRefusedFromAnyIpEvenWithTheRightPassword() throws Exception {
        TestPlayers.signUp(userService, "ratelimit_fay");
        failLoginsFromManyIps("ratelimit_fay@example.invalid", 20);

        login("198.51.100.30", " Ratelimit_Fay@Example.invalid", TestPlayers.PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(content().string(containsString(ACCOUNT_REFUSED)))
                .andExpect(unauthenticated());
    }

    @Test
    void anAccountCanLogInAgainOnceItsWindowHasPassed() throws Exception {
        TestPlayers.signUp(userService, "ratelimit_gus");
        failLoginsFromManyIps("ratelimit_gus@example.invalid", 40);

        clock.set(START.plus(game.authLimits().loginAccountWindow()));

        login("198.51.100.31", "ratelimit_gus@example.invalid", TestPlayers.PASSWORD)
                .andExpect(authenticated().withUsername("ratelimit_gus@example.invalid"));
    }

    @Test
    void oneAccountOverTheLimitDoesNotAffectAnother() throws Exception {
        TestPlayers.signUp(userService, "ratelimit_hal");
        TestPlayers.signUp(userService, "ratelimit_ivy");
        failLoginsFromManyIps("ratelimit_hal@example.invalid", 60);

        login("198.51.100.32", "ratelimit_ivy@example.invalid", TestPlayers.PASSWORD)
                .andExpect(authenticated().withUsername("ratelimit_ivy@example.invalid"));
    }

    @Test
    void successfulLoginsDoNotCountAgainstTheAccount() throws Exception {
        TestPlayers.signUp(userService, "ratelimit_jo");
        for (int i = 0; i <= game.authLimits().loginAccountMaxFailures(); i++) {
            login("198.51.100.33", "ratelimit_jo@example.invalid", TestPlayers.PASSWORD)
                    .andExpect(authenticated().withUsername("ratelimit_jo@example.invalid"));
        }
    }

    /** Wrong passwords for one account, each from its own IP so no per-IP limit is reached. */
    private void failLoginsFromManyIps(String email, int firstHost) throws Exception {
        for (int i = 0; i < game.authLimits().loginAccountMaxFailures(); i++) {
            login("203.0.113." + (firstHost + i), email, "wrong password").andExpect(redirectedUrl("/auth/login?error"));
        }
    }

    private MockHttpServletRequestBuilder forwardedFor(String client, String proxy) {
        return fromIp(post("/auth/signup"), proxy).header("X-Forwarded-For", client);
    }

    private void useUpLogins(String ip) throws Exception {
        for (int i = 0; i < game.authLimits().loginMaxAttempts(); i++) {
            login(ip, "nobody@example.invalid", "wrong password").andExpect(redirectedUrl("/auth/login?error"));
        }
    }

    private void useUpSignups(String ip) throws Exception {
        for (int i = 0; i < game.authLimits().signupMaxAttempts(); i++) {
            attemptSignup(fromIp(post("/auth/signup"), ip)).andExpect(status().isOk());
        }
    }

    /** An attempt that counts but is turned away for its invalid email, leaving no account behind. */
    private ResultActions attemptSignup(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf())
                .param("email", "not-an-email").param("displayName", "nobody").param("password", TestPlayers.PASSWORD));
    }

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mockMvc.perform(fromIp(post("/auth/login"), ip).with(csrf())
                .param("email", email).param("password", password));
    }

    private ResultActions signup(String ip, String email, String displayName) throws Exception {
        return mockMvc.perform(fromIp(post("/auth/signup"), ip).with(csrf())
                .param("email", email).param("displayName", displayName).param("password", TestPlayers.PASSWORD));
    }

    private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String ip) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }
}
