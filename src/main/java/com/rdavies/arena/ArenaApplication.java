package com.rdavies.arena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ArenaApplication {
    /** The server clock is the only authority on game timing. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }


    public static void main(String[] args) {
        SpringApplication.run(ArenaApplication.class, args);
    }
}
