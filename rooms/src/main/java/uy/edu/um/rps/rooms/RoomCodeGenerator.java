package uy.edu.um.rps.rooms;

/** Genera códigos de sala de 6 letras (estilo "Among Us"). */
@FunctionalInterface
public interface RoomCodeGenerator {
    int CODE_LENGTH = 6;

    String generate();
}
