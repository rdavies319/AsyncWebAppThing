package com.rdavies.arena.web;

import com.rdavies.arena.Wording;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.records.FightPage;
import com.rdavies.arena.fight.records.FightRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Fight history (spec section 9): GET /fights and GET /fights/{id}, each as JSON or as a plain
 * server-rendered page, by the Accept header. Public, and built from public records only.
 */
@Controller
public class FightPagesController {
    /** The most fights one history request returns. */
    static final int MAX_LIMIT = 200;

    private final FightService fightService;

    public FightPagesController(FightService fightService) {
        this.fightService = fightService;
    }

    @GetMapping(path = "/fights", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<FightRecord> history(@RequestParam(defaultValue = "50") int limit) {
        return fightService.history(Math.clamp(limit, 1, MAX_LIMIT));
    }

    /** One row of the history page. */
    public record Row(FightRecord fight, String summary) {}

    @GetMapping(path = "/fights", produces = MediaType.TEXT_HTML_VALUE)
    public String historyPage(@RequestParam(defaultValue = "50") int limit, Model model) {
        model.addAttribute("rows", history(limit).stream().map(f -> new Row(f, Wording.summary(f))).toList());
        return "fights";
    }

    @GetMapping(path = "/fights/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public FightPage fight(@PathVariable long id) {
        return fightService.page(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/fights/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String fightPage(@PathVariable long id, Model model) {
        FightPage page = fight(id);
        FightRecord fight = page.fight();
        model.addAttribute("fight", fight);
        model.addAttribute("summary", Wording.summary(fight));
        model.addAttribute("redOdds", Wording.odds(fight.red().multiplierBps()));
        model.addAttribute("blueOdds", Wording.odds(fight.blue().multiplierBps()));
        model.addAttribute("log", page.rounds().stream()
                .map(round -> Wording.roundLine(round, fight.red().name(), fight.blue().name())).toList());
        return "fight";
    }
}
