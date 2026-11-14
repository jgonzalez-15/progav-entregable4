package uy.edu.um.rps.core;

/** Resultado de una ronda desde el punto de vista del primer jugador. */
public enum Outcome {
    WIN,
    LOSE,
    DRAW;

    public Outcome inverse() {
        return switch (this) {
            case WIN -> LOSE;
            case LOSE -> WIN;
            case DRAW -> DRAW;
        };
    }
}
