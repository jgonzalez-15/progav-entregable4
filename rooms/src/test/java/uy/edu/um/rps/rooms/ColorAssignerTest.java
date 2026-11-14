package uy.edu.um.rps.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ColorAssignerTest {

    private final ColorAssigner assigner = new ColorAssigner(new Random(3));

    @Test
    void neverReturnsATakenColor() {
        EnumSet<ShirtColor> taken = EnumSet.of(ShirtColor.RED, ShirtColor.BLUE, ShirtColor.GREEN);
        for (int i = 0; i < 200; i++) {
            assertThat(assigner.pick(taken)).isNotIn(taken);
        }
    }

    @Test
    void returnsTheOnlyFreeColor() {
        EnumSet<ShirtColor> taken = EnumSet.allOf(ShirtColor.class);
        taken.remove(ShirtColor.PINK);
        assertThat(assigner.pick(taken)).isEqualTo(ShirtColor.PINK);
    }

    @Test
    void failsWhenNoColorsLeft() {
        assertThatThrownBy(() -> assigner.pick(EnumSet.allOf(ShirtColor.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allColorsHaveValidHex() {
        assertThat(List.of(ShirtColor.values()))
                .allSatisfy(c -> assertThat(c.hex()).matches("#[0-9a-f]{6}"));
    }
}
