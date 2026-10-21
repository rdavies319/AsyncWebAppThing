package com.rdavies.api.live;

import com.rdavies.api.fight.events.ExchangeRun;
import com.rdavies.api.fight.events.FightSettled;
import com.rdavies.api.fight.events.FightStarted;
import com.rdavies.api.fight.events.MatchupAnnounced;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Broadcasts the engine's events to every open stream (spec section 9), only once the tick that
 * published them has committed. Each payload is serialised once, here, and sent on the live-sender thread.
 */
@Component
class ArenaBroadcasts {
    private final LiveStreams streams;
    private final LiveSender sender;
    private final JsonMapper json;

    ArenaBroadcasts(LiveStreams streams, LiveSender sender, JsonMapper json) {
        this.streams = streams;
        this.sender = sender;
        this.json = json;
    }

    @TransactionalEventListener
    void on(MatchupAnnounced event) {
        broadcast("matchup_announced", event.fight());
    }

    @TransactionalEventListener
    void on(FightStarted event) {
        broadcast("fight_started", event);
    }

    @TransactionalEventListener
    void on(ExchangeRun event) {
        broadcast("exchange", event);
    }

    @TransactionalEventListener
    void on(FightSettled event) {
        broadcast("fight_settled", event);
    }

    private void broadcast(String name, Object payload) {
        String data = json.writeValueAsString(payload);
        sender.send(() -> streams.broadcast(name, data));
    }
}
