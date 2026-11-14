package uy.edu.um.rps.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchmakerTest {

    private MutableClock clock;
    private Matchmaker<String> mm;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        mm = new Matchmaker<>(new MatchmakingPolicy(1, 2, Duration.ofSeconds(5), Integer.MAX_VALUE), clock);
    }

    @Test
    void playersWithSimilarWinsAreMatchedImmediately() {
        mm.enqueue("ana", 10, "s1");
        clock.advance(Duration.ofMillis(100));
        mm.enqueue("beto", 11, "s2");

        List<Match<String>> matches = mm.tick();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).first().playerId()).isEqualTo("ana"); // el que más esperó
        assertThat(matches.get(0).second().attachment()).isEqualTo("s2");
        assertThat(mm.size()).isZero();
    }

    @Test
    void distantPlayersWaitUntilToleranceWidens() {
        mm.enqueue("novato", 0, null);
        mm.enqueue("experto", 7, null);

        assertThat(mm.tick()).isEmpty();
        clock.advance(Duration.ofSeconds(10)); // tolerancia 1 + 2*2 = 5
        assertThat(mm.tick()).isEmpty();
        clock.advance(Duration.ofSeconds(5));  // tolerancia 7
        assertThat(mm.tick()).hasSize(1);
    }

    @Test
    void choosesTheClosestOpponentAndThenTheOldest() {
        mm.enqueue("a", 20, null);
        clock.advance(Duration.ofSeconds(30)); // "a" ya tiene mucha tolerancia
        mm.enqueue("lejano", 5, null);
        mm.enqueue("cercano-viejo", 22, null);
        clock.advance(Duration.ofMillis(1));
        mm.enqueue("cercano-nuevo", 18, null);

        Match<String> m = mm.tick().get(0);

        assertThat(m.first().playerId()).isEqualTo("a");
        assertThat(m.second().playerId()).isEqualTo("cercano-viejo");
        assertThat(m.winsGap()).isEqualTo(2);
    }

    @Test
    void formsSeveralPairsInOneTickWithoutReusingTickets() {
        for (int i = 0; i < 6; i++) {
            mm.enqueue("p" + i, i % 3, null);
        }
        List<Match<String>> matches = mm.tick();
        assertThat(matches).hasSize(3);
        assertThat(matches.stream().flatMap(m -> java.util.stream.Stream.of(m.first(), m.second()))
                .map(Ticket::playerId)).doesNotHaveDuplicates().hasSize(6);
    }

    @Test
    void aProfileCannotQueueTwice() {
        mm.enqueue("ana", 3, "pestaña-1");
        assertThatThrownBy(() -> mm.enqueue("ana", 3, "pestaña-2")).isInstanceOf(AlreadyQueuedException.class);
        assertThat(mm.tick()).isEmpty(); // nunca se empareja consigo mismo
    }

    @Test
    void cancelRemovesTicketOnlyOnce() {
        mm.enqueue("ana", 3, null);
        assertThat(mm.isQueued("ana")).isTrue();
        assertThat(mm.cancel("ana")).isTrue();
        assertThat(mm.cancel("ana")).isFalse();
        assertThat(mm.size()).isZero();
    }

    @Test
    void reportsCurrentTolerance() {
        mm.enqueue("ana", 3, null);
        clock.advance(Duration.ofSeconds(11));
        assertThat(mm.currentTolerance("ana")).contains(5);
        assertThat(mm.currentTolerance("nadie")).isEmpty();
    }

    @Test
    void preservesArrivalOrder() {
        mm.enqueue("x", 0, null);
        mm.enqueue("y", 50, null);
        mm.enqueue("z", 100, null);
        assertThat(mm.queuedIds()).containsExactly("x", "y", "z");
    }
}
