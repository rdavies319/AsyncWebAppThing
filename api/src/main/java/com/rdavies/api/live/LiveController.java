package com.rdavies.api.live;

import com.rdavies.api.fight.FightService;
import com.rdavies.api.user.CurrentUser;
import com.rdavies.api.user.records.GamePrincipal;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Pushes game changes to game pages over Server-Sent Events. Events carry state, not ticks: the
 * page runs its countdowns from the server time. Every connect, including a browser reconnect,
 * starts with a snapshot (the server time and the current fights), so missed events never need
 * replaying. Every later write runs on the {@link LiveSender} thread.
 */
@RestController
public class LiveController {
    private final LiveStreams streams;
    private final LiveSender sender;
    private final FightService fightService;
    private final CurrentUser currentUser;

    LiveController(LiveStreams streams, LiveSender sender, FightService fightService, CurrentUser currentUser) {
        this.streams = streams;
        this.sender = sender;
        this.fightService = fightService;
        this.currentUser = currentUser;
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() throws IOException {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; the browser reconnects if it drops
        // Sent before the handler returns, so it is buffered and flushed first
        emitter.send(SseEmitter.event().name("snapshot").data(fightService.current(), MediaType.APPLICATION_JSON));
        streams.add(currentUser.principal().map(GamePrincipal::id).orElse(null), emitter);
        return emitter;
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        sender.send(streams::heartbeat);
    }
}
