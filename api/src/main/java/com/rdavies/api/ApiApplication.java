package com.rdavies.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class ApiApplication {
    /** The server clock is the only authority on round timing. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }


    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
