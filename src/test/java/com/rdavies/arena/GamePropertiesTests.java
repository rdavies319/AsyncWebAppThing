package com.rdavies.arena;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** game.* settings bound as at startup: stake options the server would always refuse stop the app starting. */
class GamePropertiesTests {

    @Test
    void theDefaultStakeOptionsAreValid() {
        assertEquals(List.of(10L, 100L, 500L), bind(Map.of()).stakeOptions());
    }

    @Test
    void theArenaCadenceDefaultsToProduction() {
        GameProperties game = bind(Map.of());

        assertEquals("0 0 * * * *", game.fightCron()); // the top of every hour
        assertEquals(Duration.ofMinutes(1), game.exchangeInterval());
        assertEquals(30, game.maxExchanges());
        assertEquals("0 * * * * *", game.engine().cron()); // every minute
        assertTrue(game.engine().scheduled());
        assertEquals("classpath:roster-dev.json", game.rosterFile());
    }

    @Test
    void aDashTurnsTheEngineScheduleOff() {
        assertFalse(bind(Map.of("game.engine.cron", "-")).engine().scheduled());
    }

    @Test
    void stakeOptionsWithinTheLimitsBind() {
        assertEquals(List.of(25L, 50L), bind(Map.of("game.stake-options", "25,50")).stakeOptions());
    }

    @Test
    void aStakeOptionBelowTheMinimumStopsStartup() {
        assertStartupFails(Map.of("game.stake-options", "5,100"),
                "game.stake-options: 5 is outside game.min-stake..game.max-stake (10..500)");
    }

    @Test
    void aStakeOptionAboveTheMaximumStopsStartup() {
        assertStartupFails(Map.of("game.stake-options", "100,1000"),
                "game.stake-options: 1000 is outside game.min-stake..game.max-stake (10..500)");
    }

    @Test
    void theLimitsThemselvesCanMoveTheOptionsOutOfRange() {
        assertStartupFails(Map.of("game.max-stake", "400"),
                "game.stake-options: 500 is outside game.min-stake..game.max-stake (10..400)");
    }

    @Test
    void environmentVariablesOverrideAnySetting() {
        // Bound as Spring Boot binds the real environment, e.g. GAME_EXCHANGE_INTERVAL=5s from .env.dev
        Map<String, Object> env = Map.of(
                "GAME_FIGHT_CRON", "0 */3 * * * *",
                "GAME_EXCHANGE_INTERVAL", "5s",
                "GAME_ENGINE_CRON", "* * * * * *",
                "GAME_STAKE_OPTIONS", "25,50");

        GameProperties game = new Binder(ConfigurationPropertySources.from(
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .bindOrCreate("game", GameProperties.class);

        assertEquals("0 */3 * * * *", game.fightCron());
        assertEquals(Duration.ofSeconds(5), game.exchangeInterval());
        assertEquals("* * * * * *", game.engine().cron());
        assertEquals(List.of(25L, 50L), game.stakeOptions());
    }

    @Test
    void theDevCadenceFitsAFightBetweenSlots() {
        GameProperties game = bind(Map.of("game.fight-cron", "0 */3 * * * *", "game.exchange-interval", "5s"));

        assertEquals(Duration.ofSeconds(5), game.exchangeInterval()); // 30 × 5s = 2.5 minutes, within 3
    }

    @Test
    void aCappedFightThatOverrunsTheNextSlotStopsStartup() {
        assertStartupFails(Map.of("game.exchange-interval", "2m"),
                "game.max-exchanges × game.exchange-interval (PT1H) must be shorter than the gap between "
                        + "two game.fight-cron slots (PT1H)");
    }

    @Test
    void theShortestGapBetweenSlotsIsTheOneThatCounts() {
        // Fights at 09:00 and 09:20, then not until the next morning
        assertStartupFails(Map.of("game.fight-cron", "0 0,20 9 * * *"),
                "game.max-exchanges × game.exchange-interval (PT30M) must be shorter than the gap between "
                        + "two game.fight-cron slots (PT20M)");
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
