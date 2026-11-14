package uy.edu.um.rps.rooms;

import java.util.Objects;

/** Jugador dentro de una sala. */
public record Player(String id, String name, ShirtColor color, Celebration celebration) {
    public Player {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(color);
        celebration = celebration == null ? Celebration.BOUNCE : celebration;
    }

    public Player(String id, String name, ShirtColor color) {
        this(id, name, color, Celebration.BOUNCE);
    }
}
