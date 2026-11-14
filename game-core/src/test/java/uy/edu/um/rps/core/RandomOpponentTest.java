package uy.edu.um.rps.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RandomOpponentTest {

    @Test
    void sameSeedProducesSameSequence() {
        RandomOpponent a = new RandomOpponent(new Random(42));
        RandomOpponent b = new RandomOpponent(new Random(42));
        for (int i = 0; i < 50; i++) {
            assertThat(a.nextMove()).isEqualTo(b.nextMove());
        }
    }

    @Test
    void distributionIsRoughlyUniform() {
        RandomOpponent opponent = new RandomOpponent(new Random(7));
        Map<Move, Integer> counts = new EnumMap<>(Move.class);
        int total = 30_000;
        for (int i = 0; i < total; i++) {
            counts.merge(opponent.nextMove(), 1, Integer::sum);
        }
        assertThat(counts).containsOnlyKeys(Move.values());
        counts.values().forEach(c -> assertThat(c).isBetween(9_000, 11_000));
    }

    @Test
    void defaultConstructorNeverReturnsNull() {
        RandomOpponent opponent = new RandomOpponent();
        for (int i = 0; i < 100; i++) {
            assertThat(opponent.nextMove()).isNotNull();
        }
    }
}
