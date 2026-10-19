package com.rdavies.api;

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
 * Drive rounds through the {@link TestGame} bean, and set up unusual wallets through {@link TestWallets}.
 * Add @Transactional per class (but see RoundEngineLockTests).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClock.Config.class, TestGame.class, TestWallets.class})
public @interface GameTest {}
