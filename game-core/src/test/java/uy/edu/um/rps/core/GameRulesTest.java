package uy.edu.um.rps.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class GameRulesTest {

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource({
            "ROCK,     SCISSORS, WIN",
            "ROCK,     PAPER,    LOSE",
            "ROCK,     ROCK,     DRAW",
            "PAPER,    ROCK,     WIN",
            "PAPER,    SCISSORS, LOSE",
            "PAPER,    PAPER,    DRAW",
            "SCISSORS, PAPER,    WIN",
            "SCISSORS, ROCK,     LOSE",
            "SCISSORS, SCISSORS, DRAW"
    })
    void evaluatesAllNineCombinations(Move first, Move second, Outcome expected) {
        assertThat(GameRules.play(first, second)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(Move.class)
    void resultIsSymmetric(Move move) {
        for (Move other : Move.values()) {
            assertThat(GameRules.play(other, move)).isEqualTo(GameRules.play(move, other).inverse());
        }
    }

    @Test
    void rejectsNullMoves() {
        assertThatThrownBy(() -> GameRules.play(null, Move.ROCK)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GameRules.play(Move.ROCK, null)).isInstanceOf(NullPointerException.class);
    }
}
