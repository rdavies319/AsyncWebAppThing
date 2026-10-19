package com.rdavies.api.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Form login with BCrypt and CSRF on (spec section 9). Watching the game is public; predicting
 * and topping up need a logged-in player.
 */
@Configuration
public class SecurityConfig {
    static final String LOGIN_PAGE = "/auth/login";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/fragments/board", "/rounds/current", "/live", "/auth.css").permitAll()
                .requestMatchers("/auth/signup", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage(LOGIN_PAGE)
                .usernameParameter("email")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/"))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    /**
     * Browsers navigating get the login page. The JSON API gets a plain 401, since redirecting a
     * fetch to an HTML page helps no one, and htmx gets a 401 with HX-Redirect so it navigates to
     * the login page instead of swapping it into the board.
     */
    private static AuthenticationEntryPoint entryPoint() {
        LoginUrlAuthenticationEntryPoint loginPage = new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE);
        return (request, response, exception) -> {
            if (request.getHeader("HX-Request") != null) {
                response.setHeader("HX-Redirect", LOGIN_PAGE);
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            } else if (isApi(request)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            } else {
                loginPage.commence(request, response, exception);
            }
        };
    }

    private static boolean isApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/predictions") || path.startsWith("/wallet/") || path.startsWith("/rounds");
    }
}
