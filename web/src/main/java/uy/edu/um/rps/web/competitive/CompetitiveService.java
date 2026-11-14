package uy.edu.um.rps.web.competitive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uy.edu.um.rps.matchmaking.AlreadyQueuedException;
import uy.edu.um.rps.matchmaking.Match;
import uy.edu.um.rps.matchmaking.Matchmaker;
import uy.edu.um.rps.rooms.MatchOutcome;
import uy.edu.um.rps.rooms.MatchSeed;
import uy.edu.um.rps.rooms.Player;
import uy.edu.um.rps.rooms.Room;
import uy.edu.um.rps.rooms.RoomException;
import uy.edu.um.rps.rooms.RoomManager;
import uy.edu.um.rps.web.config.CompetitiveProperties;
import uy.edu.um.rps.web.player.PlayerProfile;
import uy.edu.um.rps.web.player.PlayerService;
import uy.edu.um.rps.web.pvp.ServerMessages;
import uy.edu.um.rps.web.pvp.SessionRegistry;
import uy.edu.um.rps.web.pvp.SessionRegistry.Seat;
import uy.edu.um.rps.web.ranking.RankingService;

/**
 * Orquesta el modo competitivo: cola, creación de partidas, cierre y registro en el ranking.
 *
 * <p>Concurrencia:
 * <ul>
 *   <li>El emparejamiento ocurre en un único hilo programado ({@link #matchmakingTick()}) y además
 *       se intenta al encolar; {@link Matchmaker} serializa todo con un lock, así un ticket se usa una vez.</li>
 *   <li>{@code activePlayers} (putIfAbsent) impide que un perfil esté en cola o en partida dos veces
 *       (dos pestañas, dos dispositivos): evita auto-emparejarse y farmear.</li>
 *   <li>El cierre de partida (última jugada vs. abandono) lo decide {@code RoomManager} de forma atómica;
 *       la persistencia es idempotente por matchId.</li>
 * </ul>
 */
@Service
public class CompetitiveService {

    private static final Logger log = LoggerFactory.getLogger(CompetitiveService.class);

    private final Matchmaker<String> matchmaker;
    private final PlayerService players;
    private final RankingService ranking;
    private final RoomManager rooms;
    private final SessionRegistry registry;
    private final CompetitiveProperties props;
    /** playerId -> sessionId mientras el perfil está en cola o jugando. */
    private final Map<String, String> activePlayers = new ConcurrentHashMap<>();
    /** sessionId -> perfil en cola (para cancelar al desconectarse). */
    private final Map<String, PlayerProfile> queuedBySession = new ConcurrentHashMap<>();

    public CompetitiveService(Matchmaker<String> matchmaker, PlayerService players, RankingService ranking,
                              RoomManager rooms, SessionRegistry registry, CompetitiveProperties props) {
        this.matchmaker = matchmaker;
        this.players = players;
        this.ranking = ranking;
        this.rooms = rooms;
        this.registry = registry;
        this.props = props;
    }

    public void enqueue(String sessionId, String playerId, String token) {
        PlayerProfile profile = players.authenticate(playerId, token);
        String previous = activePlayers.putIfAbsent(profile.getId(), sessionId);
        if (previous != null && !previous.equals(sessionId)) {
            throw new RoomException(RoomException.Reason.ALREADY_QUEUED,
                    "Tu perfil ya está buscando o jugando una partida en otra pestaña");
        }
        // Registrar ANTES de encolar: el tick programado podría emparejar el ticket de inmediato.
        queuedBySession.put(sessionId, profile);
        try {
            matchmaker.enqueue(profile.getId(), profile.getWins(), sessionId);
        } catch (AlreadyQueuedException e) {
            queuedBySession.remove(sessionId);
            throw new RoomException(RoomException.Reason.ALREADY_QUEUED, "Ya estás en la cola");
        }
        int tolerance = matchmaker.currentTolerance(profile.getId()).orElse(props.baseTolerance());
        registry.send(sessionId, ServerMessages.queued(profile.getWins(), tolerance, matchmaker.size()));
        matchmakingTick();
    }

    /** Sale de la cola (botón cancelar o desconexión). */
    public void cancel(String sessionId) {
        PlayerProfile profile = queuedBySession.remove(sessionId);
        if (profile != null && matchmaker.cancel(profile.getId())) {
            activePlayers.remove(profile.getId(), sessionId);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void matchmakingTick() {
        List<Match<String>> matches = matchmaker.tick();
        for (Match<String> m : matches) {
            startMatch(m);
        }
        // Mantener informados a quienes siguen esperando (la tolerancia se va ensanchando)
        queuedBySession.forEach((sessionId, profile) -> matchmaker.currentTolerance(profile.getId())
                .ifPresent(t -> registry.send(sessionId, ServerMessages.queued(profile.getWins(), t, matchmaker.size()))));
    }

    private void startMatch(Match<String> m) {
        String sessionA = m.first().attachment();
        String sessionB = m.second().attachment();
        PlayerProfile a = queuedBySession.remove(sessionA);
        PlayerProfile b = queuedBySession.remove(sessionB);
        if (a == null || b == null || !registry.isOpen(sessionA) || !registry.isOpen(sessionB)) {
            // Alguien cerró la pestaña justo al ser emparejado: el otro vuelve a la cola.
            requeueIfOpen(sessionA, a);
            requeueIfOpen(sessionB, b);
            return;
        }
        Room room = rooms.createMatchRoom(new MatchSeed(a.getId(), a.getName(), a.getCelebration()),
                new MatchSeed(b.getId(), b.getName(), b.getCelebration()), props.targetWins());
        registry.seat(sessionA, new Seat(room.code(), a.getId(), true));
        registry.seat(sessionB, new Seat(room.code(), b.getId(), true));
        log.info("Partida competitiva {}: {} ({}V) vs {} ({}V)", room.matchId(), a.getName(), a.getWins(),
                b.getName(), b.getWins());
        registry.send(sessionA, ServerMessages.matchFound(room, room.player(a.getId()).orElseThrow()));
        registry.send(sessionB, ServerMessages.matchFound(room, room.player(b.getId()).orElseThrow()));
    }

    private void requeueIfOpen(String sessionId, PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        if (registry.isOpen(sessionId)) {
            matchmaker.enqueue(profile.getId(), profile.getWins(), sessionId);
            queuedBySession.put(sessionId, profile);
        } else {
            activePlayers.remove(profile.getId(), sessionId);
        }
    }

    /** Un jugador abandona (botón o desconexión) durante una partida: pierde por abandono. */
    public void forfeit(Seat seat) {
        rooms.forfeit(seat.code(), seat.playerId()).ifPresent(this::finish);
    }

    /**
     * Cierra la partida: persiste el resultado (una sola vez) y notifica a cada jugador sus nuevas
     * estadísticas y posición global.
     */
    public void finish(MatchOutcome outcome) {
        RankingService.Recorded recorded;
        try {
            recorded = ranking.record(outcome);
        } catch (DataIntegrityViolationException e) {
            log.warn("Cierre duplicado de la partida {} descartado", outcome.matchId());
            recorded = new RankingService.Recorded(outcome.matchId(), false, true);
        }
        Optional<Room> room = findRoomByMatch(outcome);
        for (String playerId : List.of(outcome.winnerId(), outcome.loserId())) {
            String sessionId = activePlayers.remove(playerId);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "MATCH_RESULT");
            msg.put("matchId", outcome.matchId());
            msg.put("winnerId", outcome.winnerId());
            msg.put("forfeit", outcome.forfeit());
            msg.put("counted", recorded.counted());
            msg.put("scores", outcome.scores());
            ranking.position(playerId).ifPresent(e -> msg.put("you", e));
            if (sessionId != null) {
                registry.removeSeat(sessionId);
                registry.send(sessionId, msg);
            }
            room.ifPresent(r -> rooms.leave(r.code(), playerId));
        }
    }

    private Optional<Room> findRoomByMatch(MatchOutcome outcome) {
        String sessionId = activePlayers.get(outcome.winnerId());
        if (sessionId == null) {
            sessionId = activePlayers.get(outcome.loserId());
        }
        return Optional.ofNullable(sessionId)
                .flatMap(registry::seatOf)
                .flatMap(seat -> rooms.find(seat.code()));
    }

    public int queueSize() {
        return matchmaker.size();
    }

    /** Para el handler: ¿el jugador dueño de esta conexión pertenece a una partida activa? */
    public boolean isActive(String playerId) {
        return activePlayers.containsKey(playerId);
    }

    public Optional<Player> playerOf(Seat seat) {
        return rooms.find(seat.code()).flatMap(r -> r.player(seat.playerId()));
    }
}
