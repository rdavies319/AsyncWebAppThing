package com.rdavies.api.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.Set;

/**
 * Form login with BCrypt and CSRF on (spec section 9). Watching the game is public; betting
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
                .requestMatchers(HttpMethod.GET, "/", "/fragments/board", "/fights/**", "/live", "/app.css", "/auth.css").permitAll()
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
     * htmx gets a 401 with HX-Redirect, so it navigates to the login page instead of swapping it
     * into the board. Browsers asking for HTML get the login page. Everything else, the JSON API
     * included, gets a plain 401, since redirecting a fetch to an HTML page helps no one.
     */
    private static AuthenticationEntryPoint entryPoint() {
        LoginUrlAuthenticationEntryPoint loginPage = new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE);
        MediaTypeRequestMatcher wantsHtml = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        wantsHtml.setIgnoredMediaTypes(Set.of(MediaType.ALL)); // "*/*" alone is not asking for a page
        return (request, response, exception) -> {
            if (request.getHeader("HX-Request") != null) {
                response.setHeader("HX-Redirect", LOGIN_PAGE);
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            } else if (wantsHtml.matches(request)) {
                loginPage.commence(request, response, exception);
            } else {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            }
        };
    }
}
