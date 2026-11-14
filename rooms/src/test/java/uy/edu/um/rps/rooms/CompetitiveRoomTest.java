package uy.edu.um.rps.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.rps.core.Move;

class CompetitiveRoomTest {

    private RoomManager manager;
    private Room room;

    @BeforeEach
    void setUp() {
        manager = new RoomManager(new RandomRoomCodeGenerator(new Random(4)), new ColorAssigner(new Random(4)));
        room = manager.createMatchRoom(new MatchSeed("ana-id", "Ana", Celebration.PEACE),
                new MatchSeed("beto-id", "Beto", Celebration.DRUM), 2);
    }

    @Test
    void matchRoomUsesProfileIdsColorsAndCelebrations() {
        assertThat(room.isCompetitive()).isTrue();
        assertThat(room.matchId()).isNotBlank();
        assertThat(room.players()).extracting(Player::id).containsExactly("ana-id", "beto-id");
        assertThat(room.players()).extracting(Player::celebration).containsExactly(Celebration.PEACE, Celebration.DRUM);
        assertThat(room.players().get(0).color()).isNotEqualTo(room.players().get(1).color());
    }

    @Test
    void cannotJoinACompetitiveRoomWithItsCode() {
        assertThatThrownBy(() -> manager.joinRoom(room.code(), "Intruso"))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.ROOM_FULL);
    }

    @Test
    void bestOfThreeEndsWhenSomeoneReachesTwoWins() {
        RoundResult r1 = play(Move.ROCK, Move.SCISSORS); // Ana 1-0
        RoundResult r2 = play(Move.ROCK, Move.ROCK);     // empate
        RoundResult r3 = play(Move.ROCK, Move.PAPER);    // 1-1
        assertThat(r1.matchOver()).isFalse();
        assertThat(r2.matchOver()).isFalse();
        assertThat(r3.matchOver()).isFalse();

        RoundResult r4 = play(Move.PAPER, Move.ROCK);    // Ana 2-1
        assertThat(r4.matchOver()).isTrue();
        MatchOutcome outcome = r4.matchOutcome();
        assertThat(outcome.winnerId()).isEqualTo("ana-id");
        assertThat(outcome.loserId()).isEqualTo("beto-id");
        assertThat(outcome.forfeit()).isFalse();
        assertThat(outcome.matchId()).isEqualTo(room.matchId());
        assertThat(outcome.scores()).containsEntry("ana-id", 2).containsEntry("beto-id", 1);

        assertThatThrownBy(() -> manager.submitMove(room.code(), "ana-id", Move.ROCK))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.MATCH_FINISHED);
    }

    @Test
    void leavingMidMatchIsAForfeitAndOnlyClosesOnce() {
        play(Move.ROCK, Move.SCISSORS); // Ana va ganando
        MatchOutcome forfeit = manager.forfeit(room.code(), "ana-id").orElseThrow();
        assertThat(forfeit.winnerId()).isEqualTo("beto-id");
        assertThat(forfeit.loserId()).isEqualTo("ana-id");
        assertThat(forfeit.forfeit()).isTrue();
        assertThat(manager.forfeit(room.code(), "beto-id")).isEmpty();
    }

    @Test
    void noForfeitAfterTheMatchAlreadyEnded() {
        play(Move.ROCK, Move.SCISSORS);
        play(Move.ROCK, Move.SCISSORS);
        assertThat(manager.forfeit(room.code(), "beto-id")).isEmpty();
    }

    @Test
    void casualRoomsNeverProduceMatchOutcomes() {
        JoinResult host = manager.createRoom("Caro", Celebration.HORNS);
        JoinResult guest = manager.joinRoom(host.room().code(), "Dani", Celebration.WAVE);
        assertThat(host.player().celebration()).isEqualTo(Celebration.HORNS);
        assertThat(manager.forfeit(host.room().code(), host.player().id())).isEmpty();
        for (int i = 0; i < 5; i++) {
            manager.submitMove(host.room().code(), host.player().id(), Move.PAPER);
            RoundResult r = manager.submitMove(host.room().code(), guest.player().id(), Move.ROCK).orElseThrow();
            assertThat(r.matchOver()).isFalse();
        }
    }

    @Test
    void cannotMatchAPlayerAgainstThemself() {
        assertThatThrownBy(() -> manager.createMatchRoom(new MatchSeed("x", "X", null), new MatchSeed("x", "X", null), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Carrera entre la última jugada y un abandono: la partida se cierra exactamente una vez. */
    @Test
    void lastMoveAndForfeitRaceProducesSingleOutcome() throws Exception {
        for (int i = 0; i < 200; i++) {
            Room r = manager.createMatchRoom(new MatchSeed("a" + i, "A", null), new MatchSeed("b" + i, "B", null), 1);
            manager.submitMove(r.code(), "a" + i, Move.ROCK);
            AtomicInteger outcomes = new AtomicInteger();
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            final int n = i;
            pool.submit(() -> {
                go.await();
                manager.submitMove(r.code(), "b" + n, Move.SCISSORS).filter(RoundResult::matchOver)
                        .ifPresent(x -> outcomes.incrementAndGet());
                return null;
            });
            pool.submit(() -> {
                go.await();
                manager.forfeit(r.code(), "b" + n).ifPresent(x -> outcomes.incrementAndGet());
                return null;
            });
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(outcomes.get()).isEqualTo(1);
        }
    }

    @Test
    void celebrationParsingFallsBackToDefault() {
        assertThat(Celebration.parseOrDefault("peace")).isEqualTo(Celebration.PEACE);
        assertThat(Celebration.parseOrDefault(" middle_finger ")).isEqualTo(Celebration.MIDDLE_FINGER);
        assertThat(Celebration.parseOrDefault("hackeo")).isEqualTo(Celebration.BOUNCE);
        assertThat(Celebration.parseOrDefault(null)).isEqualTo(Celebration.BOUNCE);
    }

    private RoundResult play(Move ana, Move beto) {
        manager.submitMove(room.code(), "ana-id", ana);
        return manager.submitMove(room.code(), "beto-id", beto).orElseThrow();
    }
}
