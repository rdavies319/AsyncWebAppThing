package com.rdavies.arena.web;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.user.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Signup and login pages. Spring Security handles the login POST and logout (SecurityConfig). */
@Controller
public class AuthController {
    private final UserService userService;
    private final GameProperties game;

    public AuthController(UserService userService, GameProperties game) {
        this.userService = userService;
        this.game = game;
    }

    /** Shown on the signup page, including when it is re-rendered with an error. */
    @ModelAttribute("signupBonus")
    public long signupBonus() {
        return game.signupBonus();
    }

    @GetMapping("/auth/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/auth/signup")
    public String signupForm() {
        return "auth/signup";
    }

    @PostMapping("/auth/signup")
    public String signup(@RequestParam(defaultValue = "") String email,
                         @RequestParam(defaultValue = "") String displayName,
                         @RequestParam(defaultValue = "") String password,
                         Model model) {
        try {
            userService.signup(email, displayName, password);
        } catch (GameRuleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            return "auth/signup";
        }
        return "redirect:/auth/login?registered";
    }
}
