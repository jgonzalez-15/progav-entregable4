package uy.edu.um.rps.web.ranking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RankingController {

    private final RankingService ranking;

    public RankingController(RankingService ranking) {
        this.ranking = ranking;
    }

    /** Top 100 + posición exacta del jugador indicado (aunque esté fuera del top). */
    @GetMapping("/api/leaderboard")
    public RankingService.Leaderboard leaderboard(@RequestParam(required = false) String playerId) {
        return ranking.leaderboard(playerId);
    }
}
