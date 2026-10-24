package com.rdavies.arena.user;

import com.rdavies.arena.user.records.GamePrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The player the current request acts as, from the security context. */
@Component
public class CurrentUser {

    /** The logged-in player, or empty for an anonymous visitor. */
    public Optional<GamePrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof GamePrincipal player ? Optional.of(player) : Optional.empty();
    }

    /**
     * The logged-in player's id. Only for endpoints that SecurityConfig restricts to logged-in
     * players, so an anonymous caller here is a configuration bug.
     */
    public long id() {
        return principal().orElseThrow(() -> new IllegalStateException("No logged-in player")).id();
    }
}
