package uy.edu.um.rps.rooms;

/** Paleta de colores de camiseta. Cada jugador de una sala recibe uno distinto. */
public enum ShirtColor {
    RED("#e5484d", "Rojo"),
    BLUE("#3e63dd", "Azul"),
    GREEN("#30a46c", "Verde"),
    YELLOW("#f5d90a", "Amarillo"),
    PURPLE("#8e4ec6", "Violeta"),
    ORANGE("#f76b15", "Naranja"),
    CYAN("#00a2c7", "Celeste"),
    PINK("#d6409f", "Rosa"),
    WHITE("#eeeeee", "Blanco"),
    BLACK("#2b2b2b", "Negro");

    private final String hex;
    private final String label;

    ShirtColor(String hex, String label) {
        this.hex = hex;
        this.label = label;
    }

    public String hex() {
        return hex;
    }

    public String label() {
        return label;
    }
}
