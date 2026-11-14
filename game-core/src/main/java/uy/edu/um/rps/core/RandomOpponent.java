package uy.edu.um.rps.core;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Oponente virtual que elige una jugada uniformemente al azar. */
public class RandomOpponent implements MoveStrategy {

    private static final Move[] MOVES = Move.values();
    private final RandomGenerator random;

    public RandomOpponent() {
        this(new SecureRandom());
    }

    /** Permite inyectar un generador con semilla fija para pruebas deterministas. */
    public RandomOpponent(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public Move nextMove() {
        return MOVES[random.nextInt(MOVES.length)];
    }
}
