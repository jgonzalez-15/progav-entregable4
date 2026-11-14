package uy.edu.um.rps.matchmaking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cola de emparejamiento por cantidad de victorias competitivas.
 *
 * <p>Algoritmo (en cada {@link #tick()}): se recorren los tickets del más antiguo al más nuevo.
 * Para cada uno se calcula su tolerancia según lo que lleva esperando y se busca, en un índice
 * ordenado por victorias ({@link TreeMap}), el rival con menor diferencia dentro de
 * {@code [wins - tolerancia, wins + tolerancia]}; ante empates gana el que más esperó.
 * La búsqueda es O(log n + k) gracias al índice.</p>
 *
 * <p>Concurrencia: todas las operaciones toman el mismo {@link ReentrantLock}, de modo que un
 * ticket se empareja o se cancela exactamente una vez, aunque {@code enqueue}, {@code cancel}
 * y {@code tick} se invoquen desde hilos distintos (conexiones WebSocket y el scheduler).</p>
 */
public class Matchmaker<T> {

    private final MatchmakingPolicy policy;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    /** Orden de llegada: prioridad para emparejar. */
    private final LinkedHashMap<String, Ticket<T>> byArrival = new LinkedHashMap<>();
    /** Índice victorias -> tickets (en orden de llegada). */
    private final NavigableMap<Integer, LinkedHashMap<String, Ticket<T>>> byWins = new TreeMap<>();

    public Matchmaker(MatchmakingPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public Ticket<T> enqueue(String playerId, int wins, T attachment) {
        lock.lock();
        try {
            if (byArrival.containsKey(playerId)) {
                throw new AlreadyQueuedException(playerId);
            }
            Ticket<T> ticket = new Ticket<>(playerId, wins, clock.instant(), attachment);
            byArrival.put(playerId, ticket);
            byWins.computeIfAbsent(wins, w -> new LinkedHashMap<>()).put(playerId, ticket);
            return ticket;
        } finally {
            lock.unlock();
        }
    }

    /** @return true si el ticket estaba en la cola (false si ya había sido emparejado). */
    public boolean cancel(String playerId) {
        lock.lock();
        try {
            Ticket<T> ticket = byArrival.remove(playerId);
            if (ticket == null) {
                return false;
            }
            removeFromIndex(ticket);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Ejecuta una ronda de emparejamiento y devuelve los pares formados. */
    public List<Match<T>> tick() {
        lock.lock();
        try {
            Instant now = clock.instant();
            List<Match<T>> matches = new ArrayList<>();
            List<Ticket<T>> snapshot = new ArrayList<>(byArrival.values());
            for (Ticket<T> ticket : snapshot) {
                if (!byArrival.containsKey(ticket.playerId())) {
                    continue; // ya emparejado en esta misma ronda
                }
                int tolerance = policy.toleranceFor(Duration.between(ticket.enqueuedAt(), now));
                findBestOpponent(ticket, tolerance).ifPresent(opponent -> {
                    remove(ticket);
                    remove(opponent);
                    matches.add(new Match<>(ticket, opponent));
                });
            }
            return matches;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return byArrival.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isQueued(String playerId) {
        lock.lock();
        try {
            return byArrival.containsKey(playerId);
        } finally {
            lock.unlock();
        }
    }

    /** Tolerancia actual de un ticket (para mostrarla en la UI). */
    public Optional<Integer> currentTolerance(String playerId) {
        lock.lock();
        try {
            Ticket<T> t = byArrival.get(playerId);
            return t == null ? Optional.empty()
                    : Optional.of(policy.toleranceFor(Duration.between(t.enqueuedAt(), clock.instant())));
        } finally {
            lock.unlock();
        }
    }

    private Optional<Ticket<T>> findBestOpponent(Ticket<T> ticket, int tolerance) {
        long low = Math.max(0L, (long) ticket.wins() - tolerance);
        long high = Math.min(Integer.MAX_VALUE, (long) ticket.wins() + tolerance);
        Ticket<T> best = null;
        for (Map.Entry<Integer, LinkedHashMap<String, Ticket<T>>> bucket
                : byWins.subMap((int) low, true, (int) high, true).entrySet()) {
            for (Ticket<T> candidate : bucket.getValue().values()) {
                if (candidate.playerId().equals(ticket.playerId())) {
                    continue; // nunca contra uno mismo
                }
                if (best == null || isBetter(ticket, candidate, best)) {
                    best = candidate;
                }
                break; // dentro de un mismo bucket, el primero es el más antiguo
            }
        }
        return Optional.ofNullable(best);
    }

    private static <T> boolean isBetter(Ticket<T> me, Ticket<T> candidate, Ticket<T> best) {
        int gapCandidate = Math.abs(candidate.wins() - me.wins());
        int gapBest = Math.abs(best.wins() - me.wins());
        if (gapCandidate != gapBest) {
            return gapCandidate < gapBest;
        }
        return candidate.enqueuedAt().isBefore(best.enqueuedAt());
    }

    private void remove(Ticket<T> ticket) {
        byArrival.remove(ticket.playerId());
        removeFromIndex(ticket);
    }

    private void removeFromIndex(Ticket<T> ticket) {
        LinkedHashMap<String, Ticket<T>> bucket = byWins.get(ticket.wins());
        if (bucket != null) {
            bucket.remove(ticket.playerId());
            if (bucket.isEmpty()) {
                byWins.remove(ticket.wins());
            }
        }
    }

    /** Solo para diagnóstico/pruebas: ids en orden de llegada. */
    List<String> queuedIds() {
        lock.lock();
        try {
            List<String> ids = new ArrayList<>();
            for (Iterator<String> it = byArrival.keySet().iterator(); it.hasNext(); ) {
                ids.add(it.next());
            }
            return ids;
        } finally {
            lock.unlock();
        }
    }
}
