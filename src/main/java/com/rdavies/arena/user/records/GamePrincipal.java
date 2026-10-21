package com.rdavies.arena.user.records;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The logged-in player. Carries the user id so requests never have to look it up by email. */
public record GamePrincipal(long id, String email, String displayName, String passwordHash) implements UserDetails {

    public static GamePrincipal of(User user) {
        return new GamePrincipal(user.id(), user.email(), user.displayName(), user.passwordHash());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
