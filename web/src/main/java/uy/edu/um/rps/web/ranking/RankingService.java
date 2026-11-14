package uy.edu.um.rps.web.ranking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.rps.rooms.MatchOutcome;
import uy.edu.um.rps.web.config.CompetitiveProperties;
import uy.edu.um.rps.web.player.PlayerProfile;
import uy.edu.um.rps.web.player.PlayerProfileRepository;

/**
 * Único punto de escritura de las estadísticas competitivas.
 * Reglas antifraude aplicadas aquí:
 * <ul>
 *   <li>Solo se llama desde el modo competitivo (resultado calculado por el servidor).</li>
 *   <li>Idempotencia por matchId: una partida no puede sumar dos veces.</li>
 *   <li>Anti win-trading: contra el mismo rival solo cuentan N victorias por día.</li>
 *   <li>Incrementos atómicos en SQL (sin lost updates).</li>
 * </ul>
 */
@Service
public class RankingService {

    public static final int TOP_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    public record Entry(long position, String playerId, String name, int wins, int losses) {
    }

    public record Leaderboard(List<Entry> top, Entry me, boolean meInTop, long totalRanked) {
    }

    public record Recorded(String matchId, boolean counted, boolean duplicate) {
    }

    private final PlayerProfileRepository players;
    private final CompetitiveMatchRepository matches;
    private final CompetitiveProperties props;
    private final Clock clock;

    public RankingService(PlayerProfileRepository players, CompetitiveMatchRepository matches,
                          CompetitiveProperties props, Clock clock) {
        this.players = players;
        this.matches = matches;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Recorded record(MatchOutcome outcome) {
        if (matches.existsById(outcome.matchId())) {
            log.warn("Partida {} ya registrada: se ignora el duplicado", outcome.matchId());
            return new Recorded(outcome.matchId(), false, true);
        }
        Instant now = clock.instant();
        long recentWins = matches.countByWinnerIdAndLoserIdAndCountedTrueAndFinishedAtAfter(
                outcome.winnerId(), outcome.loserId(), now.minus(Duration.ofDays(1)));
        boolean counted = recentWins < props.maxCountedWinsPerOpponentPerDay();
        matches.saveAndFlush(new CompetitiveMatch(outcome.matchId(), outcome.winnerId(), outcome.loserId(),
                outcome.scores().getOrDefault(outcome.winnerId(), 0), outcome.scores().getOrDefault(outcome.loserId(), 0),
                outcome.forfeit(), counted, now));
        if (counted) {
            players.addWin(outcome.winnerId(), now);
            players.addLoss(outcome.loserId());
        } else {
            log.info("Victoria de {} sobre {} no cuenta para el ranking (límite diario contra el mismo rival)",
                    outcome.winnerId(), outcome.loserId());
        }
        return new Recorded(outcome.matchId(), counted, false);
    }

    @Transactional(readOnly = true)
    public Leaderboard leaderboard(String playerId) {
        List<PlayerProfile> ranking = players.findRanking(PageRequest.of(0, TOP_SIZE));
        List<Entry> top = new ArrayList<>(ranking.size());
        for (int i = 0; i < ranking.size(); i++) {
            top.add(entry(i + 1, ranking.get(i)));
        }
        Entry me = playerId == null ? null : position(playerId).orElse(null);
        boolean meInTop = me != null && me.position() <= TOP_SIZE;
        return new Leaderboard(top, me, meInTop, players.countRanked());
    }

    /** Posición global exacta (null si nunca jugó una partida competitiva). */
    @Transactional(readOnly = true)
    public Optional<Entry> position(String playerId) {
        return players.findById(playerId).map(p -> {
            boolean ranked = p.getWins() + p.getLosses() > 0;
            long position = ranked ? players.countAhead(p.getWins(), p.getWinsReachedAt(), p.getId()) + 1 : 0;
            return entry(position, p);
        });
    }

    private static Entry entry(long position, PlayerProfile p) {
        return new Entry(position, p.getId(), p.getName(), p.getWins(), p.getLosses());
    }
}
