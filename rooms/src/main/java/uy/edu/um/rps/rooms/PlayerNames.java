package uy.edu.um.rps.rooms;

import java.util.regex.Pattern;

/** Validación y normalización de nombres de jugador. */
public final class PlayerNames {

    public static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N} _.\\-]+");

    private PlayerNames() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new RoomException(RoomException.Reason.INVALID_NAME, "El nombre es obligatorio");
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new RoomException(RoomException.Reason.INVALID_NAME, "El nombre es obligatorio");
        }
        if (name.length() > MAX_LENGTH) {
            throw new RoomException(RoomException.Reason.INVALID_NAME,
                    "El nombre puede tener hasta " + MAX_LENGTH + " caracteres");
        }
        if (!ALLOWED.matcher(name).matches()) {
            throw new RoomException(RoomException.Reason.INVALID_NAME,
                    "El nombre solo puede tener letras, números, espacios, '.', '_' o '-'");
        }
        return name;
    }
}
