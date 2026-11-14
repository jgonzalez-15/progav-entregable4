package uy.edu.um.rps.rooms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomRoomCodeGeneratorTest {

    private final RandomRoomCodeGenerator generator = new RandomRoomCodeGenerator(new Random(1));

    @Test
    void generatesSixUppercaseLettersWithoutIorO() {
        for (int i = 0; i < 1_000; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(6).matches("[A-Z]{6}").doesNotContain("I", "O");
        }
    }

    @Test
    void codesAreMostlyUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSizeGreaterThan(995);
    }

    @Test
    void validatesFormat() {
        assertThat(RandomRoomCodeGenerator.isValidFormat("ABCDEF")).isTrue();
        assertThat(RandomRoomCodeGenerator.isValidFormat("abcdef")).isFalse();
        assertThat(RandomRoomCodeGenerator.isValidFormat("ABC12F")).isFalse();
        assertThat(RandomRoomCodeGenerator.isValidFormat("ABCDE")).isFalse();
        assertThat(RandomRoomCodeGenerator.isValidFormat(null)).isFalse();
    }
}
