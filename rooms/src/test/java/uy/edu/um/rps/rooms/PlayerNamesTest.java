package uy.edu.um.rps.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerNamesTest {

    @ParameterizedTest
    @CsvSource({"'  Ana  ',Ana", "'José   Pérez',José Pérez", "jugador_1,jugador_1", "'M.-R',M.-R"})
    void normalizesValidNames(String raw, String expected) {
        assertThat(PlayerNames.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "<script>", "nombre_demasiado_largo_para_el_juego", "a;b"})
    void rejectsInvalidNames(String raw) {
        assertThatThrownBy(() -> PlayerNames.normalize(raw))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).reason())
                .isEqualTo(RoomException.Reason.INVALID_NAME);
    }
}
