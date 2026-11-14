package uy.edu.um.rps.web.pvp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uy.edu.um.rps.rooms.Player;
import uy.edu.um.rps.rooms.Room;
import uy.edu.um.rps.rooms.RoundResult;

/** Construye los mensajes que el servidor envía a los navegadores. */
public final class ServerMessages {

    private ServerMessages() {
    }

    public static Map<String, Object> player(Player p, Room room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("name", p.name());
        m.put("color", p.color().name());
        m.put("colorHex", p.color().hex());
        m.put("score", room.scores().getOrDefault(p.id(), 0));
        m.put("ready", room.hasMoved(p.id()));
        m.put("celebration", p.celebration().name());
        return m;
    }

    public static Map<String, Object> roomState(Room room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "ROOM_STATE");
        m.put("code", room.code());
        m.put("players", room.players().stream().map(p -> player(p, room)).toList());
        m.put("round", room.round());
        m.put("draws", room.draws());
        m.put("full", room.isFull());
        m.put("competitive", room.isCompetitive());
        m.put("targetWins", room.targetWins());
        return m;
    }

    public static Map<String, Object> joined(Room room, Player you) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "JOINED");
        m.put("code", room.code());
        m.put("you", player(you, room));
        m.put("room", roomState(room));
        return m;
    }

    public static Map<String, Object> roundResult(RoundResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "ROUND_RESULT");
        m.put("round", r.round());
        m.put("moves", r.moves());
        m.put("winnerId", r.winnerId());
        m.put("scores", r.scores());
        m.put("draws", r.draws());
        m.put("matchOver", r.matchOver());
        return m;
    }

    public static Map<String, Object> matchFound(Room room, Player you) {
        Map<String, Object> m = joined(room, you);
        m.put("type", "MATCH_FOUND");
        return m;
    }

    public static Map<String, Object> queued(int wins, int tolerance, int queueSize) {
        return Map.of("type", "QUEUED", "wins", wins, "tolerance", tolerance, "queueSize", queueSize);
    }

    public static Map<String, Object> playerLeft(Player p) {
        return Map.of("type", "PLAYER_LEFT", "playerId", p.id(), "name", p.name());
    }

    public static Map<String, Object> error(String reason, String message) {
        return Map.of("type", "ERROR", "reason", reason, "message", message);
    }

    public static Map<String, Object> simple(String type) {
        return Map.of("type", type);
    }

    static List<String> types() {
        return List.of("JOINED", "ROOM_STATE", "ROUND_RESULT", "PLAYER_LEFT", "LEFT", "ERROR", "PONG",
                "QUEUED", "QUEUE_CANCELLED", "MATCH_FOUND", "MATCH_RESULT");
    }
}
