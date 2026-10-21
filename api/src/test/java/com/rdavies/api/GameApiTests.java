package com.rdavies.api;

import com.rdavies.api.user.records.GamePrincipal;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** How the JSON API reports errors. The top-up itself is covered by WalletTests. */
@GameTest
@Transactional
class GameApiTests {
    @Autowired MockMvc mockMvc;

    @Test
    void internalFaultsAreNotReportedAsRuleViolations() {
        // A logged-in player with no wallet: a bug, so no 409 ProblemDetail with the internal message
        GamePrincipal ghost = new GamePrincipal(Long.MAX_VALUE, "ghost@example.invalid", "ghost", "!");
        ServletException e = assertThrows(ServletException.class,
                () -> mockMvc.perform(post("/wallet/topup").with(user(ghost)).with(csrf())));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }
}
