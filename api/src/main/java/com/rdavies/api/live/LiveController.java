package com.rdavies.api.live;

import com.rdavies.api.round.RoundOpened;
import org.springframework.http.MediaType;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Pushes round changes to open game pages over Server-Sent Events. */
@RestController
public class LiveController {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; the browser reconnects if it drops
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    // After commit, so a page that reloads on this event is guaranteed to see the new round
    @TransactionalEventListener
    public void onRoundOpened(RoundOpened event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("round").data(Map.of("roundId", event.roundId())));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
