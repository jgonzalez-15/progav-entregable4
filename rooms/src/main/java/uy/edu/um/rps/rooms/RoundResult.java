package uy.edu.um.rps.rooms;

import java.util.Map;
import uy.edu.um.rps.core.Move;

/**
 * Resultado de una ronda PvP.
 *
 * @param round    número de ronda (1..n)
 * @param moves    jugada de cada jugador (id -> jugada)
 * @param winnerId id del ganador o null si fue empate
 * @param scores   rondas ganadas acumuladas por jugador
 * @param draws    empates acumulados
 * @param matchOutcome fin de la partida competitiva (null si sigue o si la sala es casual)
 */
public record RoundResult(int round, Map<String, Move> moves, String winnerId,
                          Map<String, Integer> scores, int draws, MatchOutcome matchOutcome) {

    public RoundResult(int round, Map<String, Move> moves, String winnerId, Map<String, Integer> scores, int draws) {
        this(round, moves, winnerId, scores, draws, null);
    }

    public boolean matchOver() {
        return matchOutcome != null;
    }

    public boolean isDraw() {
        return winnerId == null;
    }
}
