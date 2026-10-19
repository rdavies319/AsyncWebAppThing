package com.rdavies.api;

import com.rdavies.api.user.GamePrincipal;
import com.rdavies.api.user.UserRepository;
import com.rdavies.api.user.UserService;
import com.rdavies.api.wallet.TestWallets;
import com.rdavies.api.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Signup, login and who may do what (spec sections 5 and 9). */
@GameTest
@Transactional
class AuthTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired WalletService walletService;
    @Autowired TestWallets wallets;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void signupCreatesThePlayerWithTheSignupBonus() throws Exception {
        signup("Alice@Example.com", "alice", "correct horse")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?registered"));

        var alice = userRepository.findByEmail("alice@example.com").orElseThrow(); // stored lower-case
        assertEquals("alice", alice.displayName());
        assertTrue(alice.passwordHash().startsWith("$2"), "BCrypt hash");
        assertEquals(1000, walletService.getWallet(alice.id()).balance());
    }

    @Test
    void duplicateEmailsAndDisplayNamesAreRejected() throws Exception {
        TestPlayers.signUp(userService, "bob");
        signup("BOB@example.invalid", "someone_else", "correct horse")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That email already has an account")));
        signup("new@example.invalid", "bob", "correct horse")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That display name is taken")))
                .andExpect(content().string(containsString("value=\"new@example.invalid\""))); // keeps the input
    }

    @Test
    void invalidSignupsAreRejected() {
        assertThrows(GameRuleException.class, () -> userService.signup("not-an-email", "carol", "correct horse"));
        assertThrows(GameRuleException.class, () -> userService.signup("c@example.invalid", "c!", "correct horse"));
        assertThrows(GameRuleException.class, () -> userService.signup("c@example.invalid", "carol", "short"));
        assertThrows(GameRuleException.class,
                () -> userService.signup("c@example.invalid", "carol", "x".repeat(73)));
        assertFalse(userRepository.existsByDisplayName("carol"));
    }

    @Test
    void loginWithTheRightPasswordAnyEmailCase() throws Exception {
        TestPlayers.signUp(userService, "dave");
        mockMvc.perform(post("/auth/login").with(csrf())
                        .param("email", "DAVE@example.invalid").param("password", TestPlayers.PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("dave@example.invalid"));
    }

    @Test
    void loginWithTheWrongPasswordFails() throws Exception {
        TestPlayers.signUp(userService, "erin");
        mockMvc.perform(post("/auth/login").with(csrf())
                        .param("email", "erin@example.invalid").param("password", "wrong password"))
                .andExpect(redirectedUrl("/auth/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void anyoneCanWatchButOnlyPlayersSeeTheFormAndWallet() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("to play.")))
                .andExpect(content().string(containsString("<nav class=\"auth-links\">")))
                .andExpect(content().string(not(containsString("Balance:"))));
        mockMvc.perform(get("/rounds/current")).andExpect(status().is(not(401)));
        mockMvc.perform(get("/live")).andExpect(request().asyncStarted());

        GamePrincipal frank = TestPlayers.signUp(userService, "frank");
        mockMvc.perform(get("/").with(user(frank)))
                .andExpect(content().string(containsString("Balance:")))
                .andExpect(content().string(containsString("frank")))
                .andExpect(content().string(containsString("Log out")))
                .andExpect(content().string(not(containsString("<nav class=\"auth-links\">"))));
    }

    @Test
    void anonymousApiCallsGet401() throws Exception {
        mockMvc.perform(post("/predictions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round_id\": 1, \"direction\": \"higher\", \"stake\": 10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/wallet/topup").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousHtmxCallsAreSentToTheLoginPage() throws Exception {
        mockMvc.perform(post("/topup").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/auth/login"));
    }

    @Test
    void anonymousPageCallsRedirectToLogin() throws Exception {
        mockMvc.perform(post("/topup").with(csrf()).accept(MediaType.TEXT_HTML, MediaType.ALL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void postsWithoutACsrfTokenAreForbidden() throws Exception {
        GamePrincipal grace = TestPlayers.signUp(userService, "grace");
        mockMvc.perform(post("/wallet/topup").with(user(grace))).andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        GamePrincipal heidi = TestPlayers.signUp(userService, "heidi");
        mockMvc.perform(post("/auth/logout").with(user(heidi)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());
    }

    private ResultActions signup(String email, String displayName, String password) throws Exception {
        return mockMvc.perform(post("/auth/signup").with(csrf())
                .param("email", email).param("displayName", displayName).param("password", password));
    }
}
