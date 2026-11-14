package uy.edu.um.rps.web.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uy.edu.um.rps.rooms.MatchOutcome;
import uy.edu.um.rps.web.player.PlayerProfileRepository;
import uy.edu.um.rps.web.player.PlayerService;

/** Ranking contra la base real: orden, posición exacta, Top 100, idempotencia y anti win-trading. */
@SpringBootTest
class RankingServiceIT {

    @Autowired private RankingService ranking;
    @Autowired private PlayerService players;
    @Autowired private PlayerProfileRepository profiles;
    @Autowired private CompetitiveMatchRepository matches;

    @BeforeEach
    void clean() {
        matches.deleteAll();
        profiles.deleteAll();
    }

    @Test
    void ordersByWinsAndLossesAreOnlyInformative() {
        String veterano = register("Veterano");
        String invicto = register("Invicto");
        // Veterano: 3 victorias y 4 derrotas. Invicto: 2 victorias, 0 derrotas.
        String sparring = register("Sparring");
        for (int i = 0; i < 3; i++) win(veterano, sparring);
        for (int i = 0; i < 4; i++) win(register("Rival" + i), veterano);
        win(invicto, register("X1"));
        win(invicto, register("X2"));

        RankingService.Leaderboard lb = ranking.leaderboard(invicto);
        assertThat(lb.top().get(0).name()).isEqualTo("Veterano");
        assertThat(lb.top().get(0).losses()).isEqualTo(4);
        assertThat(lb.top().get(1).name()).isEqualTo("Invicto");
        assertThat(lb.me().position()).isEqualTo(2);
        assertThat(lb.meInTop()).isTrue();
    }

    @Test
    void tiesAreBrokenByWhoReachedTheWinsFirst() throws Exception {
        String primero = register("Primero");
        String segundo = register("Segundo");
        win(primero, register("A1"));
        Thread.sleep(5);
        win(segundo, register("A2"));
        assertThat(ranking.position(primero).orElseThrow().position()).isEqualTo(1);
        assertThat(ranking.position(segundo).orElseThrow().position()).isEqualTo(2);
    }

    @Test
    void top100PlusExactPositionOfPlayerOutsideTheTop() {
        String rookie = register("Rookie");
        for (int i = 0; i < 130; i++) {
            String p = register("P" + i);
            win(p, register("Q" + i)); // 130 jugadores con 1 victoria (y 130 con 1 derrota)
            if (i < 120) {
                win(p, register("R" + i)); // 120 de ellos con 2 victorias
            }
        }
        win(register("Z"), rookie); // rookie: 0 victorias, 1 derrota (rankeado)

        RankingService.Leaderboard lb = ranking.leaderboard(rookie);
        assertThat(lb.top()).hasSize(RankingService.TOP_SIZE);
        assertThat(lb.top()).extracting(RankingService.Entry::position).startsWith(1L, 2L, 3L).endsWith(100L);
        assertThat(lb.meInTop()).isFalse();
        // Por delante: 120 (2V) + 10 (1V) + 1 ("Z" con 1V) = 131 -> puesto 132. Empatan en 0V los 251 perdedores.
        long withMoreWins = 131;
        assertThat(lb.me().position()).isGreaterThan(withMoreWins);
        assertThat(lb.me().position()).isEqualTo(profiles.countAhead(0,
                profiles.findById(rookie).orElseThrow().getWinsReachedAt(), rookie) + 1);
        assertThat(lb.totalRanked()).isEqualTo(profiles.countRanked());
    }

    @Test
    void sameMatchCannotBeCountedTwice() {
        String a = register("Ana");
        String b = register("Beto");
        MatchOutcome o = new MatchOutcome(UUID.randomUUID().toString(), a, b, Map.of(a, 2, b, 0), false);
        assertThat(ranking.record(o).counted()).isTrue();
        RankingService.Recorded again = ranking.record(o);
        assertThat(again.duplicate()).isTrue();
        assertThat(ranking.position(a).orElseThrow().wins()).isEqualTo(1);
        assertThat(ranking.position(b).orElseThrow().losses()).isEqualTo(1);
    }

    @Test
    void winTradingAgainstSameOpponentIsCappedPerDay() {
        String a = register("Farmer");
        String b = register("Complice");
        for (int i = 0; i < 5; i++) {
            RankingService.Recorded r = win(a, b);
            assertThat(r.counted()).as("partida %d", i + 1).isEqualTo(i < 3);
        }
        assertThat(ranking.position(a).orElseThrow().wins()).isEqualTo(3);
        assertThat(matches.count()).isEqualTo(5); // se registran todas, pero solo 3 cuentan
        assertThat(win(a, register("Otro")).counted()).isTrue(); // contra otro rival sí cuenta
    }

    @Test
    void playerWithoutCompetitiveMatchesIsNotRanked() {
        String nuevo = register("Nuevo");
        RankingService.Leaderboard lb = ranking.leaderboard(nuevo);
        assertThat(lb.me().position()).isZero();
        assertThat(lb.top()).isEmpty();
    }

    private String register(String name) {
        return players.register(name, null).profile().getId();
    }

    private RankingService.Recorded win(String winner, String loser) {
        return ranking.record(new MatchOutcome(UUID.randomUUID().toString(), winner, loser,
                Map.of(winner, 2, loser, 1), false));
    }
}
