package com.rdavies.arena;

import com.rdavies.arena.user.UserService;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The header every arena page shares: a plaque per page, the current one lit, and who is logged in. */
@GameTest
@Transactional
class PageHeaderTests {
    /** Each page, and the plaque it lights. */
    private static final Map<String, String> PAGES = Map.of(
            "/", "The Arena",
            "/gladiators", "Gladiators",
            "/fights", "Past fights",
            "/leaderboard", "Leaderboard");

    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired TestWallets wallets;

    @AfterEach
    void everyWalletMatchesItsLedger() {
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void everyPageLinksToEveryOtherAndLightsItsOwnPlaque() throws Exception {
        for (var page : PAGES.entrySet()) {
            String html = page(page.getKey(), null);
            for (var other : PAGES.entrySet()) {
                String plaque = ">" + other.getValue() + "</a>";
                assertTrue(html.contains("href=\"" + other.getKey() + "\""), page.getKey() + " links to " + other.getKey());
                assertTrue(html.contains(plaque), page.getKey() + " has the " + other.getValue() + " plaque");
                boolean lit = html.contains("aria-current=\"page\">" + other.getValue() + "</a>");
                assertEquals(other.equals(page), lit, page.getKey() + " lights " + other.getValue() + ": " + lit);
            }
        }
    }

    @Test
    void theCornerOffersLoginToVisitorsAndLogoutToPlayers() throws Exception {
        GamePrincipal gaius = TestPlayers.signUp(userService, "gaius");
        for (String path : PAGES.keySet()) {
            String visitor = page(path, null);
            assertTrue(visitor.contains("href=\"/auth/login\""), path);
            assertTrue(visitor.contains("href=\"/auth/signup\""), path);
            assertFalse(visitor.contains("Log out"), path);

            String player = page(path, gaius);
            assertTrue(player.contains(">gaius</span>"), path);
            assertTrue(player.contains("Log out"), path);
            assertFalse(player.contains("href=\"/auth/signup\""), path);
        }
    }

    private String page(String path, GamePrincipal viewer) throws Exception {
        var request = get(path).accept(MediaType.TEXT_HTML);
        if (viewer != null) {
            request = request.with(user(viewer));
        }
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }
}
