package com.rdavies.arena.live;

import com.rdavies.arena.bet.events.BetSettled;
import com.rdavies.arena.fight.events.RoundFought;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.fight.events.FightStarted;
import com.rdavies.arena.fight.events.MatchupAnnounced;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Broadcasts the engine's events to every open stream (spec section 9), and sends each settled bet
 * to its owner's streams, only once the tick that published them has committed. Each payload is serialised once, here, and sent on the live-sender thread.
 */
@Component
class ArenaBroadcasts {
    /**
     * After-commit listeners run by order, then in the order their events were published. Bets
     * settle while FightSettled is being published, so without this bet_settled would go out
     * before fight_settled.
     */
    private static final int BROADCASTS = 0;
    private static final int PERSONAL = 1;

    private final LiveStreams streams;
    private final LiveSender sender;
    private final JsonMapper json;

    ArenaBroadcasts(LiveStreams streams, LiveSender sender, JsonMapper json) {
        this.streams = streams;
        this.sender = sender;
        this.json = json;
    }

    @TransactionalEventListener
    @Order(BROADCASTS)
    void on(MatchupAnnounced event) {
        broadcast("matchup_announced", event.fight());
    }

    @TransactionalEventListener
    @Order(BROADCASTS)
    void on(FightStarted event) {
        broadcast("fight_started", event);
    }

    @TransactionalEventListener
    @Order(BROADCASTS)
    void on(RoundFought event) {
        broadcast("round", event);
    }

    @TransactionalEventListener
    @Order(BROADCASTS)
    void on(FightSettled event) {
        broadcast("fight_settled", event);
    }

    /** Personal: only the bet's owner's streams get it, after the tick's broadcasts. */
    @TransactionalEventListener
    @Order(PERSONAL)
    void on(BetSettled event) {
        String data = json.writeValueAsString(event);
        sender.send(() -> streams.sendToUser(event.userId(), "bet_settled", data));
    }

    private void broadcast(String name, Object payload) {
        String data = json.writeValueAsString(payload);
        sender.send(() -> streams.broadcast(name, data));
    }
}
