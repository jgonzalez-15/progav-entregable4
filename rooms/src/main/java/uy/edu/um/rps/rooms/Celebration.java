package uy.edu.um.rps.rooms;

import java.util.Locale;

/** Animaciones de victoria que el jugador elige en la configuración. */
public enum Celebration {
    BOUNCE("Rebote"),
    PEACE("Signo de la paz"),
    THUMBS_UP("Pulgar arriba"),
    HORNS("Cuernos rockeros"),
    FIST_PUMP("Puño al aire"),
    WAVE("Saludo burlón"),
    DRUM("Tamborileo"),
    MIDDLE_FINGER("Mala seña (censurada)");

    private final String label;

    Celebration(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Valor desconocido o vacío -> BOUNCE (nunca falla: el cliente no puede romper la partida). */
    public static Celebration parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return BOUNCE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BOUNCE;
        }
    }
}
