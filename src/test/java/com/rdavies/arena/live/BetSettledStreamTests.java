package com.rdavies.arena.live;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.MutableClock;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.TestPlayers;
import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.TestFights;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.FightResult;
import com.rdavies.arena.user.UserService;
import com.rdavies.arena.wallet.TestWallets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bet_settled reaches only the owner's streams, after the settling tick commits (spec section 9).
 * Not @Transactional, so ticks really commit; the fights and bets are removed after each test.
 */
@GameTest
class BetSettledStreamTests {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired TestGame game;
    @Autowired TestFights testFights;
    @Autowired TestWallets wallets;
    @Autowired LiveStreams streams;
    @Autowired BetService betService;
    @Autowired FightService fightService;
    @Autowired UserService userService;
    @Autowired MutableClock clock;

    long owner;
    RecordingEmitter ownerStream;
    RecordingEmitter otherPlayerStream;
    RecordingEmitter anonymousStream;

    @BeforeEach
    void connect() {
        testFights.deleteAll();
        // Signups commit here, so each run needs fresh names
        String run = String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000));
        owner = TestPlayers.signUp(userService, "owner_" + run).id();
        long other = TestPlayers.signUp(userService, "other_" + run).id();
        ownerStream = new RecordingEmitter();
        otherPlayerStream = new RecordingEmitter();
        anonymousStream = new RecordingEmitter();
        streams.add(owner, ownerStream);
        streams.add(other, otherPlayerStream);
        streams.add(null, anonymousStream);
    }

    @AfterEach
    void disconnect() {
        ownerStream.broken = true;
        otherPlayerStream.broken = true;
        anonymousStream.broken = true;
        testFights.deleteAll();
        wallets.assertEveryWalletMatchesItsLedger();
    }

    @Test
    void aSettledBetIsSentToItsOwnerOnlyAfterTheResult() throws Exception {
        game.plantFight(Winner.BLUE, EndedBy.KO);
        clock.set(Instant.parse("2026-01-01T00:30:00Z"));
        Bet bet = betService.place(owner, fightService.current().announced().fightId(), Corner.BLUE, 100L);

        FightResult result = game.finishFight();

        int arenaEvents = 3 + result.roundsDone() + 1; // announced, started, announced, rounds, settled
        ownerStream.awaitEvents(arenaEvents + 1, WAIT);
        assertEquals("fight_settled", ownerStream.eventNames().get(arenaEvents - 1));
        assertEquals("event:bet_settled\ndata:{\"user_id\":" + owner + ",\"bet_id\":" + bet.id()
                + ",\"fight_id\":" + result.fightId() + ",\"status\":\"won\",\"payout\":196,\"balance\":1096}\n\n",
                ownerStream.sent.getLast());

        anonymousStream.awaitEvents(arenaEvents, WAIT);
        otherPlayerStream.awaitEvents(arenaEvents, WAIT);
        Thread.sleep(200); // anything else sent would be in by now: the sender is one thread, in order
        assertFalse(anonymousStream.eventNames().contains("bet_settled"));
        assertFalse(otherPlayerStream.eventNames().contains("bet_settled"));
    }
}
