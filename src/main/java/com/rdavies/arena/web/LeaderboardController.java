package com.rdavies.arena.web;

import com.rdavies.arena.leaderboard.LeaderboardService;
import com.rdavies.arena.leaderboard.records.LeaderboardEntry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/** GET /leaderboard: the top balances, as JSON or a plain page by the Accept header. Public. */
@Controller
public class LeaderboardController {
    /** How many players the leaderboard shows. */
    static final int SIZE = 50;

    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @GetMapping(path = "/leaderboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<LeaderboardEntry> leaderboard() {
        return leaderboard.top(SIZE);
    }

    @GetMapping(path = "/leaderboard", produces = MediaType.TEXT_HTML_VALUE)
    public String leaderboardPage(Model model) {
        model.addAttribute("entries", leaderboard());
        return "leaderboard";
    }
}
