package com.rdavies.arena.web;

import com.rdavies.arena.fight.EngineLiveness;
import com.rdavies.arena.fight.records.EngineHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /health/engine: 200 while the engine is ticking, 503 once it has stopped. Public, for an uptime pinger. */
@RestController
public class HealthController {
    private final EngineLiveness liveness;

    public HealthController(EngineLiveness liveness) {
        this.liveness = liveness;
    }

    @GetMapping("/health/engine")
    public ResponseEntity<EngineHealth> health() {
        EngineHealth health = liveness.health();
        HttpStatus status = health.status() == EngineHealth.Status.OK ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(health);
    }
}
