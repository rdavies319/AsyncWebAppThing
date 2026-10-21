package com.rdavies.api.dev;

import com.rdavies.api.GameTest;
import com.rdavies.api.TestGame;
import com.rdavies.api.TestPlayers;
import com.rdavies.api.user.UserService;
import com.rdavies.api.user.records.GamePrincipal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without the dev profile there is no way to force a fight. */
@GameTest
@Transactional
class NoDevFightsTests {
    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;
    @Autowired UserService userService;

    @ParameterizedTest
    @ValueSource(strings = {"start", "exchange", "finish"})
    void theDevEndpointsAreMissing(String verb) throws Exception {
        GamePrincipal player = TestPlayers.signUp(userService, verb);
        game.boot();

        mockMvc.perform(post("/dev/fights/" + verb).with(user(player)).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
