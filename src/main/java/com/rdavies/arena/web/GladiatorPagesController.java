package com.rdavies.arena.web;

import com.rdavies.arena.board.Wording;
import com.rdavies.arena.fight.CareerService;
import com.rdavies.arena.fight.records.Career;
import com.rdavies.arena.fight.records.GladiatorProfile;
import com.rdavies.arena.fight.records.GladiatorSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The gladiators' public records (spec section 9): GET /gladiators and GET /gladiators/{slug}, each
 * as JSON or as a plain page by the Accept header. Public, and never showing a stat.
 */
@Controller
public class GladiatorPagesController {
    private final CareerService careers;

    public GladiatorPagesController(CareerService careers) {
        this.careers = careers;
    }

    @GetMapping(path = "/gladiators", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<GladiatorSummary> roster() {
        return careers.roster();
    }

    @GetMapping(path = "/gladiators", produces = MediaType.TEXT_HTML_VALUE)
    public String rosterPage(Model model) {
        model.addAttribute("gladiators", roster());
        return "gladiators";
    }

    @GetMapping(path = "/gladiators/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public GladiatorProfile gladiator(@PathVariable String slug) {
        return careers.profile(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** One figure on a gladiator's page, worded for display. */
    public record Figure(String label, String value) {}

    @GetMapping(path = "/gladiators/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public String gladiatorPage(@PathVariable String slug, Model model) {
        GladiatorProfile profile = gladiator(slug);
        Career r = profile.record();
        model.addAttribute("gladiator", profile);
        model.addAttribute("figures", List.of(
                new Figure("Record (W–L–D)", r.wins() + "–" + r.losses() + "–" + r.draws()),
                new Figure("Win rate", Wording.percent(r.winRateBps())),
                new Figure("Elo", String.valueOf(profile.elo())),
                new Figure("Damage dealt per round", Wording.hundredths(r.avgDamageDealtX100())),
                new Figure("Damage taken per round", Wording.hundredths(r.avgDamageTakenX100())),
                new Figure("Dodge rate", Wording.percent(r.dodgeRateBps())),
                new Figure("KO rate (of wins)", Wording.percent(r.koRateBps())),
                new Figure("Average fight length", Wording.hundredths(r.avgRoundsX100()) + " rounds")));
        return "gladiator";
    }
}
