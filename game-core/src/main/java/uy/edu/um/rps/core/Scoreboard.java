package uy.edu.um.rps.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registro continuo de rondas ganadas por cada participante.
 * Las claves son identificadores opacos (id de jugador, "CPU", etc).
 */
public class Scoreboard {

    private final Map<String, Integer> wins = new LinkedHashMap<>();
    private int rounds;
    private int draws;

    public synchronized void register(String participantId) {
        wins.putIfAbsent(Objects.requireNonNull(participantId), 0);
    }

    public synchronized void unregister(String participantId) {
        wins.remove(participantId);
    }

    /** Registra una ronda. {@code winnerId} es null si hubo empate. */
    public synchronized void recordRound(String winnerId) {
        rounds++;
        if (winnerId == null) {
            draws++;
            return;
        }
        if (!wins.containsKey(winnerId)) {
            throw new IllegalArgumentException("Participante desconocido: " + winnerId);
        }
        wins.merge(winnerId, 1, Integer::sum);
    }

    public synchronized int winsOf(String participantId) {
        return wins.getOrDefault(participantId, 0);
    }

    public synchronized int rounds() {
        return rounds;
    }

    public synchronized int draws() {
        return draws;
    }

    public synchronized Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(wins));
    }

    /** Reinicia contadores manteniendo a los participantes. */
    public synchronized void reset() {
        wins.replaceAll((k, v) -> 0);
        rounds = 0;
        draws = 0;
    }
}
