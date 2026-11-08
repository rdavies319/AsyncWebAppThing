package com.rdavies.arena;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** game.* settings bound as at startup: stake options out of order or not positive stop the app starting. */
class GamePropertiesTests {

    @Test
    void theDefaultStakeOptionsAreValid() {
        assertEquals(List.of(10L, 100L, 500L), bind(Map.of()).stakeOptions());
    }

    @Test
    void theArenaCadenceDefaultsToProduction() {
        GameProperties game = bind(Map.of());

        assertEquals("0 0 * * * *", game.fightCron()); // the top of every hour
        assertEquals(Duration.ofMinutes(1), game.roundInterval());
        assertEquals(30, game.maxRounds());
        assertEquals("0 * * * * *", game.engine().cron()); // every minute
        assertTrue(game.engine().scheduled());
        assertEquals("classpath:roster-dev.json", game.rosterFile());
    }

    @Test
    void aDashTurnsTheEngineScheduleOff() {
        assertFalse(bind(Map.of("game.engine.cron", "-")).engine().scheduled());
    }

    @Test
    void anyAscendingPositiveStakeOptionsBind() {
        assertEquals(List.of(25L, 50L, 2000L), bind(Map.of("game.stake-options", "25,50,2000")).stakeOptions());
    }

    @Test
    void aStakeOptionMustBePositive() {
        assertStartupFails(Map.of("game.stake-options", "0,100"),
                "game.stake-options must be positive and ascending: [0, 100]");
    }

    @Test
    void theStakeOptionsMustAscend() {
        // The page's coins take their metal from their position, so the order is the order shown
        assertStartupFails(Map.of("game.stake-options", "100,10"),
                "game.stake-options must be positive and ascending: [100, 10]");
        assertStartupFails(Map.of("game.stake-options", "10,10"),
                "game.stake-options must be positive and ascending: [10, 10]");
    }

    @Test
    void environmentVariablesOverrideAnySetting() {
        // Bound as Spring Boot binds the real environment, e.g. GAME_ROUND_INTERVAL=5s from a .env file
        Map<String, Object> env = Map.of(
                "GAME_FIGHT_CRON", "0 */3 * * * *",
                "GAME_ROUND_INTERVAL", "5s",
                "GAME_ENGINE_CRON", "* * * * * *",
                "GAME_STAKE_OPTIONS", "25,50");

        GameProperties game = new Binder(ConfigurationPropertySources.from(
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .bindOrCreate("game", GameProperties.class);

        assertEquals("0 */3 * * * *", game.fightCron());
        assertEquals(Duration.ofSeconds(5), game.roundInterval());
        assertEquals("* * * * * *", game.engine().cron());
        assertEquals(List.of(25L, 50L), game.stakeOptions());
    }

    @Test
    void theDevProfileRunsTheFastCadenceAndFitsAFightBetweenSlots() throws IOException {
        Properties dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));
        GameProperties game = new Binder(new MapConfigurationPropertySource(dev)).bindOrCreate("game", GameProperties.class);

        assertEquals("0 */3 * * * *", game.fightCron()); // every 3 minutes
        assertEquals(Duration.ofSeconds(5), game.roundInterval()); // 30 × 5s = 2.5 minutes, within 3
        assertEquals("* * * * * *", game.engine().cron()); // every second
    }

    @Test
    void aCappedFightThatOverrunsTheNextSlotStopsStartup() {
        assertStartupFails(Map.of("game.round-interval", "2m"),
                "game.max-rounds × game.round-interval (PT1H) must be shorter than the gap between "
                        + "two game.fight-cron slots (PT1H)");
    }

    @Test
    void theShortestGapBetweenSlotsIsTheOneThatCounts() {
        // Fights at 09:00 and 09:20, then not until the next morning
        assertStartupFails(Map.of("game.fight-cron", "0 0,20 9 * * *"),
                "game.max-rounds × game.round-interval (PT30M) must be shorter than the gap between "
                        + "two game.fight-cron slots (PT20M)");
    }

    @Test
    void theAuthLimitsDefaultToProduction() {
        GameProperties.AuthLimits limits = bind(Map.of()).authLimits();

        assertEquals(20, limits.loginMaxAttempts());
        assertEquals(Duration.ofMinutes(15), limits.loginWindow());
        assertEquals(5, limits.signupMaxAttempts());
        assertEquals(Duration.ofHours(1), limits.signupWindow());
        assertEquals(100_000, limits.maxTrackedIps());
    }

    @Test
    void environmentVariablesOverrideTheAuthLimits() {
        Map<String, Object> env = Map.of(
                "GAME_AUTH_LIMITS_LOGIN_MAX_ATTEMPTS", "7",
                "GAME_AUTH_LIMITS_SIGNUP_WINDOW", "2h");

        GameProperties.AuthLimits limits = new Binder(ConfigurationPropertySources.from(
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .bindOrCreate("game", GameProperties.class).authLimits();

        assertEquals(7, limits.loginMaxAttempts());
        assertEquals(Duration.ofHours(2), limits.signupWindow());
    }

    @Test
    void authLimitsMustBePositive() {
        assertStartupFails(Map.of("game.auth-limits.login-max-attempts", "0"),
                "game.auth-limits.login-max-attempts must be positive: 0");
        assertStartupFails(Map.of("game.auth-limits.login-window", "0s"),
                "game.auth-limits.login-window must be positive: PT0S");
        assertStartupFails(Map.of("game.auth-limits.signup-max-attempts", "-1"),
                "game.auth-limits.signup-max-attempts must be positive: -1");
        assertStartupFails(Map.of("game.auth-limits.signup-window", "-1m"),
                "game.auth-limits.signup-window must be positive: PT-1M");
        assertStartupFails(Map.of("game.auth-limits.max-tracked-ips", "0"),
                "game.auth-limits.max-tracked-ips must be positive: 0");
    }

    private static GameProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("game", GameProperties.class);
    }

    private static void assertStartupFails(Map<String, String> properties, String message) {
        BindException e = assertThrows(BindException.class, () -> bind(properties));
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertEquals(message, cause.getMessage());
    }
}
