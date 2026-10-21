package com.rdavies.arena.fight;

import com.rdavies.arena.fight.events.ExchangeRun;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.fight.events.FightStarted;
import com.rdavies.arena.fight.events.MatchupAnnounced;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records the engine's events as they are delivered after commit, for any GameTest to inspect. */
public class TestFightEvents {
    public final List<Object> delivered = new CopyOnWriteArrayList<>();

    @TransactionalEventListener
    void on(MatchupAnnounced event) {
        delivered.add(event);
    }

    @TransactionalEventListener
    void on(FightStarted event) {
        delivered.add(event);
    }

    @TransactionalEventListener
    void on(ExchangeRun event) {
        delivered.add(event);
    }

    @TransactionalEventListener
    void on(FightSettled event) {
        delivered.add(event);
    }

    public List<Class<?>> types() {
        return delivered.stream().<Class<?>>map(Object::getClass).toList();
    }
}
