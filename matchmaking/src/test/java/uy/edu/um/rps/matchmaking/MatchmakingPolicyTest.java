package uy.edu.um.rps.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MatchmakingPolicyTest {

    @Test
    void toleranceWidensWithWaitingTime() {
        MatchmakingPolicy p = new MatchmakingPolicy(1, 2, Duration.ofSeconds(5), 9);
        assertThat(p.toleranceFor(Duration.ZERO)).isEqualTo(1);
        assertThat(p.toleranceFor(Duration.ofSeconds(4))).isEqualTo(1);
        assertThat(p.toleranceFor(Duration.ofSeconds(5))).isEqualTo(3);
        assertThat(p.toleranceFor(Duration.ofSeconds(12))).isEqualTo(5);
        assertThat(p.toleranceFor(Duration.ofMinutes(10))).isEqualTo(9); // tope
    }

    @Test
    void negativeWaitIsTreatedAsZero() {
        assertThat(MatchmakingPolicy.defaults().toleranceFor(Duration.ofSeconds(-3))).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPolicies() {
        assertThatThrownBy(() -> new MatchmakingPolicy(-1, 1, Duration.ofSeconds(1), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchmakingPolicy(1, 1, Duration.ZERO, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
