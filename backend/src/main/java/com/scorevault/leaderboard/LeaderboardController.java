package com.scorevault.leaderboard;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/rounds")
    public List<RoundOptionView> rounds() {
        return leaderboardService.listRounds();
    }

    @GetMapping("/leaderboard/{roundId}")
    public List<LeaderboardEntryView> leaderboard(@PathVariable int roundId) {
        return leaderboardService.getLeaderboard(roundId);
    }
}
