package com.rdavies.arena.user;

import com.rdavies.arena.GameProperties;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.ViewResolver;

import java.time.Clock;
import java.util.List;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, Clock clock, GameProperties game, UserService users,
                                            @Qualifier("thymeleafViewResolver") ViewResolver views) throws Exception {
        GameProperties.AuthLimits limits = game.authLimits();
        // Keyed by real accounts only, so the number of players already bounds it
        AttemptLimiter accountFailures = new AttemptLimiter(
                clock, limits.loginAccountMaxFailures(), limits.loginAccountWindow(), Integer.MAX_VALUE);
        http
            .addFilterBefore(authRateLimitFilter(clock, game, accountFailures, views), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/fragments/board", "/fights/**", "/gladiators/**", "/leaderboard", "/live", "/health/engine", "/arena.css").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/health/engine").permitAll() // uptime pingers may use HEAD
                .requestMatchers("/auth/signup", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage(LOGIN_PAGE)
                .usernameParameter("email")
                .failureHandler(new AccountLoginFailures(LOGIN_PAGE + "?error", accountFailures, users))
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/"))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    private static AuthRateLimitFilter authRateLimitFilter(Clock clock, GameProperties game, AttemptLimiter accountFailures,
                                                           ViewResolver views) {
        GameProperties.AuthLimits limits = game.authLimits();
        RequestMatcher login = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGIN_PAGE);
        return new AuthRateLimitFilter(List.of(
                AuthRateLimitFilter.Rule.perIp(
                        login,
                        new AttemptLimiter(clock, limits.loginMaxAttempts(), limits.loginWindow(), limits.maxTrackedIps()),
                        "auth/login"),
                new AuthRateLimitFilter.Rule(
                        login, AccountLoginFailures::key, false, accountFailures,
                        "auth/login", AuthRateLimitFilter.TOO_MANY_FAILURES),
                AuthRateLimitFilter.Rule.perIp(
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/signup"),
                        new AttemptLimiter(clock, limits.signupMaxAttempts(), limits.signupWindow(), limits.maxTrackedIps()),
                        "auth/signup")),
                views, game.signupBonus());
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
