package uy.edu.um.rps.web.pvp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.rooms.Celebration;
import uy.edu.um.rps.rooms.JoinResult;
import uy.edu.um.rps.rooms.Player;
import uy.edu.um.rps.rooms.Room;
import uy.edu.um.rps.rooms.RoomException;
import uy.edu.um.rps.rooms.RoomManager;
import uy.edu.um.rps.rooms.RoundResult;
import uy.edu.um.rps.web.competitive.CompetitiveService;
import uy.edu.um.rps.web.history.HistoryService;
import uy.edu.um.rps.web.history.MatchRecord;
import uy.edu.um.rps.web.player.InvalidCredentialsException;
import uy.edu.um.rps.web.pvp.SessionRegistry.Seat;

/**
 * Punto de entrada WebSocket. Cada conexión es un jugador.
 * Modo casual (salas con código) y modo competitivo (cola de matchmaking).
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final RoomManager rooms;
    private final HistoryService history;
    private final ObjectMapper mapper;
    private final SessionRegistry registry;
    private final CompetitiveService competitive;

    public GameWebSocketHandler(RoomManager rooms, HistoryService history, ObjectMapper mapper,
                                SessionRegistry registry, CompetitiveService competitive) {
        this.rooms = rooms;
        this.history = history;
        this.mapper = mapper;
        this.registry = registry;
        this.competitive = competitive;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage text) {
        WebSocketSession session = registry.get(raw.getId()).orElse(raw);
        try {
            ClientMessage msg = mapper.readValue(text.getPayload(), ClientMessage.class);
            String type = msg.type() == null ? "" : msg.type().toUpperCase();
            switch (type) {
                case "CREATE" -> create(session, msg);
                case "JOIN" -> join(session, msg);
                case "MOVE" -> move(session, msg);
                case "LEAVE" -> {
                    leave(session.getId());
                    registry.send(session, ServerMessages.simple("LEFT"));
                }
                case "QUEUE" -> {
                    leave(session.getId());
                    competitive.enqueue(session.getId(), msg.playerId(), msg.token());
                }
                case "CANCEL_QUEUE" -> {
                    competitive.cancel(session.getId());
                    registry.send(session, ServerMessages.simple("QUEUE_CANCELLED"));
                }
                case "PING" -> registry.send(session, ServerMessages.simple("PONG"));
                default -> registry.send(session, ServerMessages.error("UNKNOWN_TYPE", "Tipo de mensaje desconocido: " + type));
            }
        } catch (RoomException e) {
            registry.send(session, ServerMessages.error(e.reason().name(), e.getMessage()));
        } catch (InvalidCredentialsException e) {
            registry.send(session, ServerMessages.error("INVALID_PROFILE", e.getMessage()));
        } catch (JsonProcessingException e) {
            registry.send(session, ServerMessages.error("BAD_JSON", "Mensaje mal formado"));
        } catch (IllegalArgumentException e) {
            registry.send(session, ServerMessages.error("BAD_REQUEST", e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        competitive.cancel(session.getId());
        leave(session.getId());
        registry.unregister(session.getId());
    }

    private void create(WebSocketSession session, ClientMessage msg) {
        leave(session.getId());
        JoinResult r = rooms.createRoom(msg.name(), Celebration.parseOrDefault(msg.celebration()));
        registry.seat(session.getId(), new Seat(r.room().code(), r.player().id(), false));
        log.info("Sala {} creada por {}", r.room().code(), r.player().name());
        registry.send(session, ServerMessages.joined(r.room(), r.player()));
    }

    private void join(WebSocketSession session, ClientMessage msg) {
        leave(session.getId());
        JoinResult r = rooms.joinRoom(msg.code(), msg.name(), Celebration.parseOrDefault(msg.celebration()));
        registry.seat(session.getId(), new Seat(r.room().code(), r.player().id(), false));
        log.info("{} se unió a la sala {}", r.player().name(), r.room().code());
        registry.send(session, ServerMessages.joined(r.room(), r.player()));
        registry.broadcast(r.room().code(), ServerMessages.roomState(r.room()));
    }

    private void move(WebSocketSession session, ClientMessage msg) {
        Seat seat = registry.seatOf(session.getId())
                .orElseThrow(() -> new RoomException(RoomException.Reason.NOT_IN_ROOM, "No estás en ninguna sala"));
        Move move = Move.parse(msg.move());
        Optional<RoundResult> result = rooms.submitMove(seat.code(), seat.playerId(), move);
        Room room = rooms.find(seat.code()).orElseThrow();
        if (result.isEmpty()) {
            // Avisamos que este jugador ya eligió (sin revelar la jugada).
            registry.broadcast(seat.code(), ServerMessages.roomState(room));
            return;
        }
        RoundResult r = result.get();
        registry.broadcast(seat.code(), ServerMessages.roundResult(r));
        saveHistory(room, r);
        if (r.matchOver()) {
            competitive.finish(r.matchOutcome());
        }
    }

    private void leave(String sessionId) {
        Optional<Seat> current = registry.seatOf(sessionId);
        if (current.isEmpty()) {
            return;
        }
        Seat seat = current.get();
        if (seat.competitive()) {
            // Abandonar una partida competitiva = derrota (evita "rage quit" para no perder puntos)
            competitive.forfeit(seat);
            registry.removeSeat(sessionId);
            return;
        }
        registry.removeSeat(sessionId);
        Optional<Player> leaving = rooms.find(seat.code()).flatMap(r -> r.player(seat.playerId()));
        Optional<Room> remaining = rooms.leave(seat.code(), seat.playerId());
        remaining.ifPresent(room -> {
            leaving.ifPresent(p -> registry.broadcast(room.code(), ServerMessages.playerLeft(p)));
            registry.broadcast(room.code(), ServerMessages.roomState(room));
        });
        leaving.ifPresent(p -> log.info("{} salió de la sala {}", p.name(), seat.code()));
    }

    private void saveHistory(Room room, RoundResult r) {
        var players = room.players();
        if (players.size() < 2) {
            return;
        }
        Player a = players.get(0);
        Player b = players.get(1);
        String winner = r.winnerId() == null ? null : room.player(r.winnerId()).map(Player::name).orElse(null);
        MatchRecord.Mode mode = room.isCompetitive() ? MatchRecord.Mode.RANKED : MatchRecord.Mode.PVP;
        history.record(new MatchRecord(mode, room.code(), a.name(), r.moves().get(a.id()),
                b.name(), r.moves().get(b.id()), winner));
    }
}
