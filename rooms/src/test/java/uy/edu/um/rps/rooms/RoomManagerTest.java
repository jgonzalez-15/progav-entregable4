package uy.edu.um.rps.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.rps.core.Move;

class RoomManagerTest {

    private RoomManager manager;

    @BeforeEach
    void setUp() {
        manager = new RoomManager(new RandomRoomCodeGenerator(new Random(10)), new ColorAssigner(new Random(10)));
    }

    @Test
    void createRoomReturnsCodeAndFirstPlayer() {
        JoinResult result = manager.createRoom("Ana");
        assertThat(result.room().code()).matches("[A-Z]{6}");
        assertThat(result.player().name()).isEqualTo("Ana");
        assertThat(result.room().players()).containsExactly(result.player());
        assertThat(manager.roomCount()).isEqualTo(1);
    }

    @Test
    void secondPlayerJoinsWithDifferentColor() {
        JoinResult host = manager.createRoom("Ana");
        JoinResult guest = manager.joinRoom(host.room().code().toLowerCase(), "Beto");
        assertThat(guest.room()).isSameAs(host.room());
        assertThat(guest.player().color()).isNotEqualTo(host.player().color());
        assertThat(host.room().isFull()).isTrue();
    }

    @Test
    void colorsAreAlwaysUniqueAcrossManyRooms() {
        for (int i = 0; i < 200; i++) {
            JoinResult host = manager.createRoom("A" + i);
            JoinResult guest = manager.joinRoom(host.room().code(), "B" + i);
            assertThat(guest.player().color()).isNotEqualTo(host.player().color());
        }
    }

    @Test
    void cannotJoinFullRoom() {
        String code = manager.createRoom("Ana").room().code();
        manager.joinRoom(code, "Beto");
        assertThatThrownBy(() -> manager.joinRoom(code, "Caro"))
                .isInstanceOf(RoomException.class)
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.ROOM_FULL);
    }

    @Test
    void cannotJoinUnknownOrMalformedRoom() {
        assertThatThrownBy(() -> manager.joinRoom("ZZZZZZ", "Ana"))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.ROOM_NOT_FOUND);
        assertThatThrownBy(() -> manager.joinRoom("AB1", "Ana"))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.INVALID_CODE);
    }

    @Test
    void cannotRepeatNameInSameRoom() {
        String code = manager.createRoom("Ana").room().code();
        assertThatThrownBy(() -> manager.joinRoom(code, "ana"))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.NAME_TAKEN);
    }

    @Test
    void roundResolvesOnlyWhenBothPlayersMoved() {
        JoinResult host = manager.createRoom("Ana");
        JoinResult guest = manager.joinRoom(host.room().code(), "Beto");
        String code = host.room().code();

        Optional<RoundResult> partial = manager.submitMove(code, host.player().id(), Move.ROCK);
        assertThat(partial).isEmpty();

        RoundResult result = manager.submitMove(code, guest.player().id(), Move.SCISSORS).orElseThrow();
        assertThat(result.round()).isEqualTo(1);
        assertThat(result.winnerId()).isEqualTo(host.player().id());
        assertThat(result.moves()).containsEntry(host.player().id(), Move.ROCK)
                .containsEntry(guest.player().id(), Move.SCISSORS);
        assertThat(result.scores()).containsEntry(host.player().id(), 1).containsEntry(guest.player().id(), 0);
    }

    @Test
    void keepsRunningScoreAcrossRoundsIncludingDraws() {
        JoinResult host = manager.createRoom("Ana");
        JoinResult guest = manager.joinRoom(host.room().code(), "Beto");
        String code = host.room().code();
        String a = host.player().id();
        String b = guest.player().id();

        play(code, a, Move.PAPER, b, Move.ROCK);      // gana A
        play(code, a, Move.PAPER, b, Move.SCISSORS);  // gana B
        play(code, a, Move.ROCK, b, Move.ROCK);       // empate
        RoundResult last = play(code, a, Move.SCISSORS, b, Move.PAPER); // gana A

        assertThat(last.round()).isEqualTo(4);
        assertThat(last.draws()).isEqualTo(1);
        assertThat(last.scores()).containsEntry(a, 2).containsEntry(b, 1);
    }

    @Test
    void drawHasNoWinner() {
        JoinResult host = manager.createRoom("Ana");
        JoinResult guest = manager.joinRoom(host.room().code(), "Beto");
        RoundResult r = play(host.room().code(), host.player().id(), Move.PAPER, guest.player().id(), Move.PAPER);
        assertThat(r.isDraw()).isTrue();
        assertThat(r.winnerId()).isNull();
    }

    @Test
    void cannotMoveTwiceOrAlone() {
        JoinResult host = manager.createRoom("Ana");
        String code = host.room().code();
        assertThatThrownBy(() -> manager.submitMove(code, host.player().id(), Move.ROCK))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.WAITING_FOR_OPPONENT);

        manager.joinRoom(code, "Beto");
        manager.submitMove(code, host.player().id(), Move.ROCK);
        assertThatThrownBy(() -> manager.submitMove(code, host.player().id(), Move.PAPER))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.ALREADY_MOVED);
        assertThatThrownBy(() -> manager.submitMove(code, "intruso", Move.PAPER))
                .hasFieldOrPropertyWithValue("reason", RoomException.Reason.NOT_IN_ROOM);
    }

    @Test
    void leavingFreesSlotAndEmptyRoomIsDeleted() {
        JoinResult host = manager.createRoom("Ana");
        JoinResult guest = manager.joinRoom(host.room().code(), "Beto");
        String code = host.room().code();

        Optional<Room> remaining = manager.leave(code, guest.player().id());
        assertThat(remaining).isPresent();
        assertThat(remaining.get().players()).extracting(Player::name).containsExactly("Ana");

        JoinResult newcomer = manager.joinRoom(code, "Caro");
        assertThat(newcomer.room().scores()).containsEntry(newcomer.player().id(), 0);

        manager.leave(code, host.player().id());
        manager.leave(code, newcomer.player().id());
        assertThat(manager.find(code)).isEmpty();
        assertThat(manager.roomCount()).isZero();
    }

    @Test
    void retriesWhenGeneratedCodeCollides() {
        Deque<String> codes = new ArrayDeque<>(List.of("AAAAAA", "AAAAAA", "BBBBBB"));
        RoomManager m = new RoomManager(codes::pop, new ColorAssigner(new Random(1)));
        assertThat(m.createRoom("Ana").room().code()).isEqualTo("AAAAAA");
        assertThat(m.createRoom("Beto").room().code()).isEqualTo("BBBBBB");
    }

    @Test
    void evictsIdleRooms() {
        manager.createRoom("Ana");
        assertThat(manager.evictIdle(Duration.ofHours(1))).isEmpty();
        assertThat(manager.evictIdle(Duration.ofMillis(-1))).hasSize(1);
        assertThat(manager.roomCount()).isZero();
    }

    private RoundResult play(String code, String a, Move ma, String b, Move mb) {
        manager.submitMove(code, a, ma);
        return manager.submitMove(code, b, mb).orElseThrow();
    }
}
