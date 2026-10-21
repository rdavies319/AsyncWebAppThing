package com.rdavies.arena.fight;

import com.rdavies.arena.GameProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Ticks the engine on game.engine.cron, and once at startup after the roster has loaded, so a
 * restart picks up straight away. A cron of "-" turns both off.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class FightSchedule implements SchedulingConfigurer, ApplicationRunner {
    private final FightEngine engine;
    private final GameProperties game;

    FightSchedule(FightEngine engine, GameProperties game) {
        this.engine = engine;
        this.game = game;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (game.engine().scheduled()) {
            registrar.addCronTask(engine::tick, game.engine().cron());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (game.engine().scheduled()) {
            engine.tick();
        }
    }
}
