package uy.edu.um.rps.matchmaking;

import java.time.Instant;
import java.util.Objects;

/**
 * Entrada en la cola competitiva.
 *
 * @param playerId   identidad del perfil (un perfil solo puede tener un ticket)
 * @param wins       victorias competitivas al momento de entrar a la cola
 * @param enqueuedAt instante de ingreso (define prioridad y tolerancia)
 * @param attachment dato opaco del llamador (ej. id de la conexión WebSocket)
 */
public record Ticket<T>(String playerId, int wins, Instant enqueuedAt, T attachment) {
    public Ticket {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt");
        if (wins < 0) {
            throw new IllegalArgumentException("wins no puede ser negativo");
        }
    }
}
