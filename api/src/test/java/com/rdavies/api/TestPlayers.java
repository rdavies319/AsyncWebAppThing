package com.rdavies.api;

import com.rdavies.api.user.UserService;
import com.rdavies.api.user.records.GamePrincipal;
/** Real signups for tests: each player gets a wallet with the signup bonus, as in production. */
final class TestPlayers {
    static final String PASSWORD = "correct horse";

    private TestPlayers() {}

    static GamePrincipal signUp(UserService users, String name) {
        return users.signup(name + "@example.invalid", name, PASSWORD);
    }
}
