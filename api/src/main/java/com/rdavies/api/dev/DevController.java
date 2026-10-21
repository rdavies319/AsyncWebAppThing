package com.rdavies.api.dev;

import com.rdavies.api.fight.FightForcing;
import com.rdavies.api.fight.FightService;
import com.rdavies.api.fight.records.CurrentFights;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dev forcing endpoints (spec section 7), under the dev profile only, behind login and CSRF like
 * every other POST. Each returns what's on afterwards, as GET /fights/current does. Refusals are
 * ProblemDetails (GameApiExceptionHandler).
 */
@RestController
@Profile("dev")
@RequestMapping("/dev/fights")
public class DevController {
    private final FightForcing forcing;
    private final FightService fightService;

    public DevController(FightForcing forcing, FightService fightService) {
        this.forcing = forcing;
        this.fightService = fightService;
    }

    @PostMapping("/start")
    public CurrentFights start() {
        forcing.start();
        return fightService.current();
    }

    @PostMapping("/exchange")
    public CurrentFights exchange() {
        forcing.exchange();
        return fightService.current();
    }

    @PostMapping("/finish")
    public CurrentFights finish() {
        forcing.finish();
        return fightService.current();
    }
}
