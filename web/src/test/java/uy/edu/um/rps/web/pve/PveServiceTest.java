package uy.edu.um.rps.web.pve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.core.Outcome;
import uy.edu.um.rps.rooms.ColorAssigner;
import uy.edu.um.rps.rooms.RoomException;
import uy.edu.um.rps.web.history.HistoryService;
import uy.edu.um.rps.web.history.MatchRecord;
import uy.edu.um.rps.web.pve.PveDtos.RoundView;
import uy.edu.um.rps.web.pve.PveDtos.SessionView;

/** Prueba unitaria: la CPU siempre juega PIEDRA y el historial es un mock. */
class PveServiceTest {

    private HistoryService history;
    private PveService service;

    @BeforeEach
    void setUp() {
        history = mock(HistoryService.class);
        service = new PveService(() -> Move.ROCK, new ColorAssigner(new Random(5)), history);
    }

    @Test
    void createsSessionWithDistinctShirtColors() {
        SessionView s = service.create("  Ana ");
        assertThat(s.player().name()).isEqualTo("Ana");
        assertThat(s.cpu().name()).isEqualTo("CPU");
        assertThat(s.player().color()).isNotEqualTo(s.cpu().color());
        assertThat(s.score().rounds()).isZero();
    }

    @Test
    void keepsRunningScoreAgainstCpu() {
        String id = service.create("Ana").sessionId();

        RoundView win = service.play(id, Move.PAPER);
        RoundView lose = service.play(id, Move.SCISSORS);
        RoundView draw = service.play(id, Move.ROCK);

        assertThat(win.outcome()).isEqualTo(Outcome.WIN);
        assertThat(lose.outcome()).isEqualTo(Outcome.LOSE);
        assertThat(draw.outcome()).isEqualTo(Outcome.DRAW);
        assertThat(draw.cpuMove()).isEqualTo(Move.ROCK);
        assertThat(draw.score()).isEqualTo(new PveDtos.Score(1, 1, 1, 3));
        assertThat(draw.round()).isEqualTo(3);
        verify(history, times(3)).record(any(MatchRecord.class));
    }

    @Test
    void unknownSessionFails() {
        assertThatThrownBy(() -> service.play("nope", Move.ROCK)).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void invalidNameIsRejected() {
        assertThatThrownBy(() -> service.create("   ")).isInstanceOf(RoomException.class);
    }

    @Test
    void endingSessionRemovesIt() {
        String id = service.create("Ana").sessionId();
        service.end(id);
        assertThat(service.activeSessions()).isZero();
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void evictsIdleSessions() {
        service.create("Ana");
        assertThat(service.evictIdle(Duration.ofMinutes(10))).isZero();
        assertThat(service.evictIdle(Duration.ofMillis(-1))).isEqualTo(1);
    }
}
