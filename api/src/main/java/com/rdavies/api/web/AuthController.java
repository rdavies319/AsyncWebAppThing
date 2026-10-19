package com.rdavies.api.web;

import com.rdavies.api.user.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Signup and login pages. Spring Security handles the login POST and logout (SecurityConfig). */
@Controller
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
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
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            return "auth/signup";
        }
        return "redirect:/auth/login?registered";
    }
}
