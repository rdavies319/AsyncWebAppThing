package com.rdavies.api;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Makes a .env file imported as properties (application-dev.properties imports .env.dev for
 * spring-boot:run) bind like environment variables. Its keys are written for compose's env_file, as
 * GAME_EXCHANGE_INTERVAL=5s, and Spring Boot only maps that form onto game.exchange-interval for a
 * system environment source, so each imported .env file is re-added as one. Registered in
 * META-INF/spring.factories; runs after the config files have loaded.
 */
public class DotEnvFiles implements EnvironmentPostProcessor, Ordered {
    /** An imported config file's source name mentions its file, e.g. "Config resource 'file [../.env.dev]' …". */
    private static final Pattern DOT_ENV_FILE = Pattern.compile("^Config resource '.*[\\[/\\\\]\\.env[^/\\\\\\]]*\\]'.*");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<PropertySource<?>> dotEnvFiles = environment.getPropertySources().stream()
                .filter(source -> source instanceof EnumerablePropertySource<?>)
                .filter(source -> DOT_ENV_FILE.matcher(source.getName()).matches())
                .toList();
        for (PropertySource<?> source : dotEnvFiles) {
            Map<String, Object> variables = new LinkedHashMap<>();
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                variables.put(name, source.getProperty(name));
            }
            // Spring Boot treats a source as environment variables by its type and this name suffix
            environment.getPropertySources().replace(source.getName(), new SystemEnvironmentPropertySource(
                    source.getName() + "-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
