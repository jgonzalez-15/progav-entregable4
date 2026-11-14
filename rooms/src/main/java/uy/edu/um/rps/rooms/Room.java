package uy.edu.um.rps.rooms;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uy.edu.um.rps.core.GameRules;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.core.Outcome;
import uy.edu.um.rps.core.Scoreboard;

/**
 * Sala de dos jugadores. No es thread-safe por sí misma: {@link RoomManager}
 * sincroniza el acceso.
 */
public class Room {

    public static final int CAPACITY = 2;

    private final String code;
    /** 0 = sala casual sin fin; N = partida competitiva al mejor de (2N-1). */
    private final int targetWins;
    private final String matchId;
    private boolean finished;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<String, Move> pendingMoves = new LinkedHashMap<>();
    private final Scoreboard scoreboard = new Scoreboard();
    private Instant lastActivity = Instant.now();

    Room(String code) {
        this(code, 0);
    }

    Room(String code, int targetWins) {
        this.code = code;
        this.targetWins = targetWins;
        this.matchId = targetWins > 0 ? UUID.randomUUID().toString() : null;
    }

    public boolean isCompetitive() {
        return targetWins > 0;
    }

    public int targetWins() {
        return targetWins;
    }

    public String matchId() {
        return matchId;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Marca la partida como terminada. Devuelve false si ya lo estaba (garantiza un único cierre). */
    boolean finish() {
        if (finished) {
            return false;
        }
        finished = true;
        return true;
    }

    String opponentOf(String playerId) {
        return players.keySet().stream().filter(id -> !id.equals(playerId)).findFirst().orElse(null);
    }

    public String code() {
        return code;
    }

    public List<Player> players() {
        return List.copyOf(players.values());
    }

    public Optional<Player> player(String playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    public boolean isFull() {
        return players.size() >= CAPACITY;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public int round() {
        return scoreboard.rounds();
    }

    public Map<String, Integer> scores() {
        return scoreboard.snapshot();
    }

    public int draws() {
        return scoreboard.draws();
    }

    public boolean hasMoved(String playerId) {
        return pendingMoves.containsKey(playerId);
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    void add(Player player) {
        players.put(player.id(), player);
        scoreboard.register(player.id());
        // Cuando entra un rival nuevo, el marcador empieza de cero para ambos.
        scoreboard.reset();
        pendingMoves.clear();
        touch();
    }

    void remove(String playerId) {
        players.remove(playerId);
        scoreboard.unregister(playerId);
        pendingMoves.clear();
        touch();
    }

    /** Registra la jugada; si ambos jugaron, resuelve la ronda. */
    Optional<RoundResult> submit(String playerId, Move move) {
        pendingMoves.put(playerId, move);
        touch();
        if (pendingMoves.size() < CAPACITY) {
            return Optional.empty();
        }
        List<String> ids = new ArrayList<>(pendingMoves.keySet());
        Move first = pendingMoves.get(ids.get(0));
        Move second = pendingMoves.get(ids.get(1));
        Outcome outcome = GameRules.play(first, second);
        String winner = switch (outcome) {
            case WIN -> ids.get(0);
            case LOSE -> ids.get(1);
            case DRAW -> null;
        };
        scoreboard.recordRound(winner);
        MatchOutcome matchOutcome = null;
        if (isCompetitive() && winner != null && scoreboard.winsOf(winner) >= targetWins && finish()) {
            matchOutcome = new MatchOutcome(matchId, winner, opponentOf(winner), scoreboard.snapshot(), false);
        }
        RoundResult result = new RoundResult(scoreboard.rounds(), Map.copyOf(pendingMoves), winner,
                scoreboard.snapshot(), scoreboard.draws(), matchOutcome);
        pendingMoves.clear();
        return Optional.of(result);
    }

    private void touch() {
        lastActivity = Instant.now();
    }
}
