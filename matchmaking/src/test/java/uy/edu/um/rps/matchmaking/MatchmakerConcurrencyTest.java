package uy.edu.um.rps.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Prueba de estrés: muchos hilos encolan y cancelan mientras otro hilo empareja.
 * Invariante: ningún jugador aparece en dos partidas ni en una partida después de cancelar.
 */
class MatchmakerConcurrencyTest {

    @RepeatedTest(3)
    void noTicketIsMatchedTwiceUnderContention() throws Exception {
        Matchmaker<Integer> mm = new Matchmaker<>(new MatchmakingPolicy(100, 0, Duration.ofSeconds(1), 100),
                Clock.systemUTC());
        int players = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> cancelled = ConcurrentHashMap.newKeySet();
        List<Match<Integer>> matches = new CopyOnWriteArrayList<>();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread ticker = new Thread(() -> {
            while (running.get()) {
                matches.addAll(mm.tick());
            }
        });
        ticker.start();

        for (int i = 0; i < players; i++) {
            final int n = i;
            pool.submit(() -> {
                start.await();
                String id = "p" + n;
                mm.enqueue(id, n % 50, n);
                if (n % 7 == 0 && mm.cancel(id)) {
                    cancelled.add(id);
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        running.set(false);
        ticker.join();
        matches.addAll(mm.tick());

        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (Match<Integer> m : matches) {
            assertThat(seen.add(m.first().playerId())).as("duplicado %s", m.first().playerId()).isTrue();
            assertThat(seen.add(m.second().playerId())).as("duplicado %s", m.second().playerId()).isTrue();
            assertThat(m.first().playerId()).isNotEqualTo(m.second().playerId());
        }
        assertThat(seen).doesNotContainAnyElementsOf(cancelled);
        // Todo jugador terminó emparejado, cancelado o (como mucho uno) esperando
        assertThat(seen.size() + cancelled.size() + mm.size()).isEqualTo(players);
        assertThat(mm.size()).isLessThanOrEqualTo(1);
    }
}
