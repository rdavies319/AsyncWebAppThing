package com.rdavies.arena.web;

import com.rdavies.arena.board.Wording;
import com.rdavies.arena.fight.CareerService;
import com.rdavies.arena.fight.records.Career;
import com.rdavies.arena.fight.records.GladiatorProfile;
import com.rdavies.arena.fight.records.GladiatorSummary;
import com.rdavies.arena.fight.records.HeadToHead;
import com.rdavies.arena.fight.records.RecentResult;
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

    /** One row of the roster page. */
    public record RosterRow(GladiatorSummary gladiator, String record) {}

    @GetMapping(path = "/gladiators", produces = MediaType.TEXT_HTML_VALUE)
    public String rosterPage(Model model) {
        model.addAttribute("rows", roster().stream()
                .map(g -> new RosterRow(g, Wording.record(g.wins(), g.losses(), g.draws()))).toList());
        return "gladiators";
    }

    @GetMapping(path = "/gladiators/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public GladiatorProfile gladiator(@PathVariable String slug) {
        return careers.profile(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** One figure on a gladiator's page, worded for display. */
    public record Figure(String label, String value) {}

    /** A head-to-head row, with its W–L–D worded. */
    public record OpponentRow(HeadToHead opponent, String record) {}

    /** A recent result, worded from the gladiator's side. */
    public record ResultRow(RecentResult fight, String result) {}

    @GetMapping(path = "/gladiators/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public String gladiatorPage(@PathVariable String slug, Model model) {
        GladiatorProfile profile = gladiator(slug);
        Career r = profile.record();
        model.addAttribute("gladiator", profile);
        model.addAttribute("figures", List.of(
                new Figure("Record (W–L–D)", Wording.record(r.wins(), r.losses(), r.draws())),
                new Figure("Win rate", Wording.percent(r.winRateBps())),
                new Figure("Elo", String.valueOf(profile.elo())),
                new Figure("Damage dealt per round", Wording.hundredths(r.avgDamageDealtX100())),
                new Figure("Damage taken per round", Wording.hundredths(r.avgDamageTakenX100())),
                new Figure("Dodge rate", Wording.percent(r.dodgeRateBps())),
                new Figure("KO rate (of wins)", Wording.percent(r.koRateBps())),
                new Figure("Average fight length", Wording.hundredths(r.avgRoundsX100()) + " rounds")));
        model.addAttribute("opponents", profile.headToHead().stream()
                .map(h -> new OpponentRow(h, Wording.record(h.wins(), h.losses(), h.draws()))).toList());
        model.addAttribute("results", profile.lastResults().stream()
                .map(f -> new ResultRow(f, Wording.recentResult(f.result(), f.endedBy()))).toList());
        return "gladiator";
    }
}
