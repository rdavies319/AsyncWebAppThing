package com.rdavies.arena.web;

import com.rdavies.arena.user.CurrentUser;
import com.rdavies.arena.user.records.GamePrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * What the shared page header ({@code header.html}) needs from every arena page: the logged-in
 * player's display name for its corner, or null for a visitor.
 */
@ControllerAdvice(assignableTypes = {GameController.class, FightPagesController.class,
        GladiatorPagesController.class, LeaderboardController.class})
class PageHeader {
    private final CurrentUser currentUser;

    PageHeader(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute("viewer")
    String viewer() {
        return currentUser.principal().map(GamePrincipal::displayName).orElse(null);
    }
}
