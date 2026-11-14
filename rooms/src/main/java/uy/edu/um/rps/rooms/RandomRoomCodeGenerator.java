package uy.edu.um.rps.rooms;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Códigos aleatorios de 6 letras mayúsculas. Se excluyen I y O para evitar
 * confusiones con 1 y 0 al dictar el código en voz alta.
 */
public class RandomRoomCodeGenerator implements RoomCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final RandomGenerator random;

    public RandomRoomCodeGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static boolean isValidFormat(String code) {
        return code != null && code.matches("[A-Z]{" + CODE_LENGTH + "}");
    }
}
