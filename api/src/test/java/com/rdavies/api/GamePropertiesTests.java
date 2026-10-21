package com.rdavies.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

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
