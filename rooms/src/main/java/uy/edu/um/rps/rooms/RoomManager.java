package uy.edu.um.rps.rooms;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import uy.edu.um.rps.core.Move;

/** Punto de entrada thread-safe para crear salas, unirse, jugar y salir. */
public class RoomManager {

    private static final int MAX_CODE_ATTEMPTS = 1_000;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomCodeGenerator codeGenerator;
    private final ColorAssigner colorAssigner;

    public RoomManager(RoomCodeGenerator codeGenerator, ColorAssigner colorAssigner) {
        this.codeGenerator = codeGenerator;
        this.colorAssigner = colorAssigner;
    }

    public synchronized JoinResult createRoom(String playerName) {
        return createRoom(playerName, Celebration.BOUNCE);
    }

    public synchronized JoinResult createRoom(String playerName, Celebration celebration) {
        String name = PlayerNames.normalize(playerName);
        String code = uniqueCode();
        Room room = new Room(code);
        Player player = newPlayer(room, UUID.randomUUID().toString(), name, celebration);
        room.add(player);
        rooms.put(code, room);
        return new JoinResult(room, player);
    }

    public synchronized JoinResult joinRoom(String rawCode, String playerName) {
        return joinRoom(rawCode, playerName, Celebration.BOUNCE);
    }

    public synchronized JoinResult joinRoom(String rawCode, String playerName, Celebration celebration) {
        String name = PlayerNames.normalize(playerName);
        Room room = requireRoom(rawCode);
        if (room.isFull() || room.isCompetitive()) {
            throw new RoomException(RoomException.Reason.ROOM_FULL, "La sala " + room.code() + " está llena");
        }
        boolean nameTaken = room.players().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        if (nameTaken) {
            throw new RoomException(RoomException.Reason.NAME_TAKEN, "Ya hay un jugador llamado " + name);
        }
        Player player = newPlayer(room, UUID.randomUUID().toString(), name, celebration);
        room.add(player);
        return new JoinResult(room, player);
    }

    /**
     * Crea la sala privada de una partida competitiva entre dos perfiles ya emparejados.
     * No se puede entrar con el código: solo la usan los dos jugadores asignados.
     */
    public synchronized Room createMatchRoom(MatchSeed a, MatchSeed b, int targetWins) {
        if (targetWins < 1) {
            throw new IllegalArgumentException("targetWins debe ser >= 1");
        }
        if (a.playerId().equals(b.playerId())) {
            throw new IllegalArgumentException("Un jugador no puede enfrentarse a sí mismo");
        }
        Room room = new Room(uniqueCode(), targetWins);
        room.add(newPlayer(room, a.playerId(), a.name(), a.celebration()));
        room.add(newPlayer(room, b.playerId(), b.name(), b.celebration()));
        rooms.put(room.code(), room);
        return room;
    }

    /**
     * Un jugador abandona una partida competitiva en curso: pierde por abandono.
     * Atómico respecto de {@link #submitMove}: la partida se cierra una sola vez.
     */
    public synchronized Optional<MatchOutcome> forfeit(String code, String leavingPlayerId) {
        Room room = rooms.get(normalizeCode(code));
        if (room == null || !room.isCompetitive() || room.player(leavingPlayerId).isEmpty() || !room.finish()) {
            return Optional.empty();
        }
        String winner = room.opponentOf(leavingPlayerId);
        if (winner == null) {
            return Optional.empty();
        }
        return Optional.of(new MatchOutcome(room.matchId(), winner, leavingPlayerId, room.scores(), true));
    }

    /** Devuelve el resultado de la ronda si ambos jugadores ya eligieron. */
    public synchronized Optional<RoundResult> submitMove(String code, String playerId, Move move) {
        Room room = requireRoom(code);
        if (room.player(playerId).isEmpty()) {
            throw new RoomException(RoomException.Reason.NOT_IN_ROOM, "No pertenecés a esta sala");
        }
        if (!room.isFull()) {
            throw new RoomException(RoomException.Reason.WAITING_FOR_OPPONENT, "Esperando a un oponente");
        }
        if (room.isFinished()) {
            throw new RoomException(RoomException.Reason.MATCH_FINISHED, "La partida ya terminó");
        }
        if (room.hasMoved(playerId)) {
            throw new RoomException(RoomException.Reason.ALREADY_MOVED, "Ya elegiste tu jugada en esta ronda");
        }
        return room.submit(playerId, move);
    }

    /** Saca al jugador de la sala. Devuelve la sala si sigue existiendo. */
    public synchronized Optional<Room> leave(String code, String playerId) {
        Room room = rooms.get(normalizeCode(code));
        if (room == null) {
            return Optional.empty();
        }
        room.remove(playerId);
        if (room.isEmpty()) {
            rooms.remove(room.code());
            return Optional.empty();
        }
        return Optional.of(room);
    }

    public Optional<Room> find(String code) {
        return Optional.ofNullable(rooms.get(normalizeCode(code)));
    }

    public int roomCount() {
        return rooms.size();
    }

    /** Elimina salas sin actividad (por ejemplo jugadores que cerraron el navegador). */
    public synchronized List<String> evictIdle(Duration maxIdle) {
        Instant limit = Instant.now().minus(maxIdle);
        List<String> evicted = rooms.values().stream()
                .filter(r -> r.lastActivity().isBefore(limit))
                .map(Room::code)
                .toList();
        evicted.forEach(rooms::remove);
        return evicted;
    }

    private Room requireRoom(String rawCode) {
        String code = normalizeCode(rawCode);
        if (!RandomRoomCodeGenerator.isValidFormat(code)) {
            throw new RoomException(RoomException.Reason.INVALID_CODE, "El código debe tener 6 letras");
        }
        Room room = rooms.get(code);
        if (room == null) {
            throw new RoomException(RoomException.Reason.ROOM_NOT_FOUND, "No existe la sala " + code);
        }
        return room;
    }

    private String uniqueCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = codeGenerator.generate();
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de sala único");
    }

    private Player newPlayer(Room room, String id, String name, Celebration celebration) {
        Set<ShirtColor> taken = room.players().stream().map(Player::color)
                .collect(Collectors.toCollection(HashSet::new));
        return new Player(id, name, colorAssigner.pick(taken), celebration);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
