package com.rdavies.api;

import com.rdavies.api.fight.TestFightEvents;
import com.rdavies.api.fight.TestFights;
import com.rdavies.api.wallet.TestWallets;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The one test setup: every class using it shares a Spring context, and so one Postgres container.
 * Drive the game through the {@link TestGame} bean, and set up unusual wallets through {@link TestWallets}.
 * Add @Transactional per class, except where a test takes an advisory lock from another thread.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClock.Config.class, TestGame.class, TestWallets.class,
        TestFights.class, TestFightEvents.class})
public @interface GameTest {}
