package uy.edu.um.rps.rooms;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Asigna un color de camiseta al azar que no esté usado por otro jugador de la sala. */
public class ColorAssigner {

    private final RandomGenerator random;

    public ColorAssigner(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public ShirtColor pick(Collection<ShirtColor> taken) {
        List<ShirtColor> free = Arrays.stream(ShirtColor.values())
                .filter(c -> !taken.contains(c))
                .toList();
        if (free.isEmpty()) {
            throw new IllegalStateException("No quedan colores libres");
        }
        return free.get(random.nextInt(free.size()));
    }
}
