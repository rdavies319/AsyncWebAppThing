package com.rdavies.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A .env file imported as properties (application-dev.properties, for spring-boot:run) binds like
 * the environment variables compose would set from the same file.
 */
class DotEnvFilesTests {

    @Test
    void anImportedDotEnvFileBindsLikeEnvironmentVariables() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'file [.env.dev]' via location 'optional:file:.env.dev[.properties]'",
                Map.of("GAME_EXCHANGE_INTERVAL", "5s", "GAME_ENGINE_CRON", "* * * * * *")));

        new DotEnvFiles().postProcessEnvironment(env, new SpringApplication());

        GameProperties game = Binder.get(env).bindOrCreate("game", GameProperties.class);
        assertEquals(Duration.ofSeconds(5), game.exchangeInterval());
        assertEquals("* * * * * *", game.engine().cron());
    }

    @Test
    void otherConfigFilesAreLeftAlone() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.properties]'",
                Map.of("GAME_EXCHANGE_INTERVAL", "5s")));

        new DotEnvFiles().postProcessEnvironment(env, new SpringApplication());

        assertEquals(Duration.ofMinutes(1), Binder.get(env).bindOrCreate("game", GameProperties.class).exchangeInterval());
    }
}
