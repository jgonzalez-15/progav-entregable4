package uy.edu.um.rps.web.pvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Conexiones abiertas y en qué sala está sentada cada una.
 * Las sesiones se envuelven en {@link ConcurrentWebSocketSessionDecorator}: varios hilos
 * (el del rival, el del matchmaking) pueden enviar a la misma conexión sin corromper frames.
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    /** Asiento de una conexión: sala + jugador (+ si es una partida competitiva). */
    public record Seat(String code, String playerId, boolean competitive) {
    }

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Seat> seats = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public SessionRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public WebSocketSession register(WebSocketSession raw) {
        WebSocketSession s = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 64 * 1024);
        sessions.put(raw.getId(), s);
        return s;
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public Optional<WebSocketSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public boolean isOpen(String sessionId) {
        WebSocketSession s = sessions.get(sessionId);
        return s != null && s.isOpen();
    }

    public void seat(String sessionId, Seat seat) {
        seats.put(sessionId, seat);
    }

    public Optional<Seat> seatOf(String sessionId) {
        return Optional.ofNullable(seats.get(sessionId));
    }

    public Optional<Seat> removeSeat(String sessionId) {
        return Optional.ofNullable(seats.remove(sessionId));
    }

    /** Conexión sentada en la sala {@code code} como el jugador {@code playerId}. */
    public Optional<String> sessionOf(String code, String playerId) {
        return seats.entrySet().stream()
                .filter(e -> e.getValue().code().equals(code) && e.getValue().playerId().equals(playerId))
                .map(Map.Entry::getKey).findFirst();
    }

    public void send(String sessionId, Object payload) {
        WebSocketSession s = sessions.get(sessionId);
        if (s != null) {
            send(s, payload);
        }
    }

    public void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            log.debug("No se pudo enviar mensaje a {}: {}", session.getId(), e.getMessage());
        }
    }

    public void broadcast(String code, Object payload) {
        seats.forEach((sessionId, seat) -> {
            if (seat.code().equals(code)) {
                send(sessionId, payload);
            }
        });
    }
}
