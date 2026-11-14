package uy.edu.um.rps.core;

import java.util.Objects;

/** Reglas clásicas: piedra vence tijera, tijera vence papel, papel vence piedra. */
public final class GameRules {

    private GameRules() {
    }

    public static Outcome play(Move first, Move second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first == second) {
            return Outcome.DRAW;
        }
        return first.beats(second) ? Outcome.WIN : Outcome.LOSE;
    }
}
