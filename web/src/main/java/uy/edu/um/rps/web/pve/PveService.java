package uy.edu.um.rps.web.pve;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import uy.edu.um.rps.core.GameRules;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.core.MoveStrategy;
import uy.edu.um.rps.core.Outcome;
import uy.edu.um.rps.core.Scoreboard;
import uy.edu.um.rps.rooms.ColorAssigner;
import uy.edu.um.rps.rooms.Player;
import uy.edu.um.rps.rooms.PlayerNames;
import uy.edu.um.rps.rooms.ShirtColor;
import uy.edu.um.rps.web.history.HistoryService;
import uy.edu.um.rps.web.history.MatchRecord;
import uy.edu.um.rps.web.pve.PveDtos.PlayerView;
import uy.edu.um.rps.web.pve.PveDtos.RoundView;
import uy.edu.um.rps.web.pve.PveDtos.Score;
import uy.edu.um.rps.web.pve.PveDtos.SessionView;

/** Lógica del modo jugador contra oponente virtual (CPU). */
@Service
public class PveService {

    public static final String CPU_NAME = "CPU";

    private final Map<String, PveSession> sessions = new ConcurrentHashMap<>();
    private final MoveStrategy cpuStrategy;
    private final ColorAssigner colorAssigner;
    private final HistoryService historyService;

    public PveService(MoveStrategy cpuStrategy, ColorAssigner colorAssigner, HistoryService historyService) {
        this.cpuStrategy = cpuStrategy;
        this.colorAssigner = colorAssigner;
        this.historyService = historyService;
    }

    public SessionView create(String rawName) {
        String name = PlayerNames.normalize(rawName);
        ShirtColor playerColor = colorAssigner.pick(EnumSet.noneOf(ShirtColor.class));
        ShirtColor cpuColor = colorAssigner.pick(EnumSet.of(playerColor));
        Player player = new Player(UUID.randomUUID().toString(), name, playerColor);
        Player cpu = new Player("cpu", CPU_NAME, cpuColor);
        PveSession session = new PveSession(UUID.randomUUID().toString(), player, cpu);
        sessions.put(session.id(), session);
        return view(session);
    }

    public SessionView get(String sessionId) {
        return view(require(sessionId));
    }

    public RoundView play(String sessionId, Move playerMove) {
        PveSession session = require(sessionId);
        Move cpuMove = cpuStrategy.nextMove();
        Outcome outcome = GameRules.play(playerMove, cpuMove);
        String winnerId = switch (outcome) {
            case WIN -> session.player().id();
            case LOSE -> session.cpu().id();
            case DRAW -> null;
        };
        Score score;
        synchronized (session) {
            session.scoreboard().recordRound(winnerId);
            session.touch();
            score = score(session);
        }
        String winnerName = switch (outcome) {
            case WIN -> session.player().name();
            case LOSE -> CPU_NAME;
            case DRAW -> null;
        };
        historyService.record(new MatchRecord(MatchRecord.Mode.PVE, null, session.player().name(), playerMove,
                CPU_NAME, cpuMove, winnerName));
        return new RoundView(score.rounds(), playerMove, cpuMove, outcome, score);
    }

    public void end(String sessionId) {
        sessions.remove(sessionId);
    }

    public int activeSessions() {
        return sessions.size();
    }

    public int evictIdle(Duration maxIdle) {
        Instant limit = Instant.now().minus(maxIdle);
        int before = sessions.size();
        sessions.values().removeIf(s -> s.lastActivity().isBefore(limit));
        return before - sessions.size();
    }

    private PveSession require(String sessionId) {
        PveSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return session;
    }

    private SessionView view(PveSession s) {
        return new SessionView(s.id(), PlayerView.of(s.player()), PlayerView.of(s.cpu()), score(s));
    }

    private static Score score(PveSession s) {
        Scoreboard b = s.scoreboard();
        return new Score(b.winsOf(s.player().id()), b.winsOf(s.cpu().id()), b.draws(), b.rounds());
    }
}
