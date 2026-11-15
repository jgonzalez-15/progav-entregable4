package uy.edu.um.rps.core;

import java.util.Locale;

/** Las tres jugadas posibles. */
public enum Move {
    ROCK("Piedra"),
    PAPER("Papel"),
    SCISSORS("Tijera");

    private final String spanishName;

    Move(String spanishName) {
        this.spanishName = spanishName;
    }

    public String spanishName() {
        return spanishName;
    }

    /** true si esta jugada le gana a {@code other}. */
    public boolean beats(Move other) {
        return switch (this) {
            case ROCK -> other == SCISSORS;
            case PAPER -> other == SCISSORS;
            case SCISSORS -> other == PAPER;
        };
    }

    /**
     * Interpreta una jugada aceptando el nombre en inglés o español,
     * sin importar mayúsculas (ej: "rock", "PIEDRA", "tijera").
     */
    public static Move parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La jugada no puede ser vacía");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (Move move : values()) {
            if (move.name().equals(value) || move.spanishName.toUpperCase(Locale.ROOT).equals(value)) {
                return move;
            }
        }
        throw new IllegalArgumentException("Jugada inválida: " + raw);
    }
}
