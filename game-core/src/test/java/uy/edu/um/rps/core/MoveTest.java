package uy.edu.um.rps.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MoveTest {

    @ParameterizedTest
    @CsvSource({"rock,ROCK", "PIEDRA,ROCK", " papel ,PAPER", "Paper,PAPER", "tijera,SCISSORS", "scissors,SCISSORS"})
    void parsesEnglishAndSpanishNames(String raw, Move expected) {
        assertThat(Move.parse(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "lagarto", "spock"})
    void rejectsInvalidInput(String raw) {
        assertThatThrownBy(() -> Move.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachMoveBeatsExactlyOneOther() {
        for (Move move : Move.values()) {
            long beaten = java.util.Arrays.stream(Move.values()).filter(move::beats).count();
            assertThat(beaten).as("%s vence a una sola jugada", move).isEqualTo(1);
            assertThat(move.beats(move)).isFalse();
        }
    }

    @Test
    void hasSpanishNames() {
        assertThat(Move.ROCK.spanishName()).isEqualTo("Piedra");
        assertThat(Move.PAPER.spanishName()).isEqualTo("Papel");
        assertThat(Move.SCISSORS.spanishName()).isEqualTo("Tijera");
    }
}
