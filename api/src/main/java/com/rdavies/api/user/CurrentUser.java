package com.rdavies.api.user;

import org.springframework.stereotype.Component;

/**
 * The player a request acts as. Until auth lands (T9) everyone plays as the demo user seeded by
 * V2__demo_user.sql; this is the one place to swap for the authenticated user.
 */
@Component
public class CurrentUser {
    private static final long DEMO_USER_ID = 1L;

    public long id() {
        return DEMO_USER_ID;
    }
}
