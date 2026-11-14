package uy.edu.um.rps.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoreboardTest {

    private Scoreboard board;

    @BeforeEach
    void setUp() {
        board = new Scoreboard();
        board.register("ana");
        board.register("CPU");
    }

    @Test
    void countsWinsRoundsAndDraws() {
        board.recordRound("ana");
        board.recordRound("ana");
        board.recordRound("CPU");
        board.recordRound(null);

        assertThat(board.winsOf("ana")).isEqualTo(2);
        assertThat(board.winsOf("CPU")).isEqualTo(1);
        assertThat(board.rounds()).isEqualTo(4);
        assertThat(board.draws()).isEqualTo(1);
        assertThat(board.snapshot()).containsEntry("ana", 2).containsEntry("CPU", 1);
    }

    @Test
    void rejectsUnknownWinner() {
        assertThatThrownBy(() -> board.recordRound("pepe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetKeepsParticipants() {
        board.recordRound("ana");
        board.reset();
        assertThat(board.snapshot()).containsEntry("ana", 0).containsEntry("CPU", 0);
        assertThat(board.rounds()).isZero();
    }

    @Test
    void unregisterRemovesParticipant() {
        board.unregister("CPU");
        assertThat(board.snapshot()).containsOnlyKeys("ana");
    }

    @Test
    void snapshotIsImmutable() {
        assertThatThrownBy(() -> board.snapshot().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
