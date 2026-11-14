package uy.edu.um.rps.rooms;

import java.util.Map;

/**
 * Fin de una partida competitiva.
 *
 * @param matchId  identificador único (clave de idempotencia al persistir)
 * @param winnerId perfil ganador
 * @param loserId  perfil perdedor
 * @param scores   rondas ganadas por cada uno
 * @param forfeit  true si terminó porque un jugador abandonó
 */
public record MatchOutcome(String matchId, String winnerId, String loserId, Map<String, Integer> scores,
                           boolean forfeit) {
}
