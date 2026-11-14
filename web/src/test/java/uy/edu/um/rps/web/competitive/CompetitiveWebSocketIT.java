package uy.edu.um.rps.web.competitive;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import uy.edu.um.rps.web.player.PlayerProfileRepository;
import uy.edu.um.rps.web.pvp.PvpWebSocketIT.TestClient;
import uy.edu.um.rps.web.ranking.CompetitiveMatchRepository;

/** Flujo competitivo completo con dos navegadores simulados y la base real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompetitiveWebSocketIT {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private PlayerProfileRepository profiles;
    @Autowired private CompetitiveMatchRepository matches;

    private TestClient a;
    private TestClient b;

    record Profile(String id, String token, String name) {
    }

    @BeforeEach
    void clean() {
        matches.deleteAll();
        profiles.deleteAll();
    }

    @AfterEach
    void close() throws Exception {
        if (a != null && a.session.isOpen()) a.session.close();
        if (b != null && b.session.isOpen()) b.session.close();
    }

    @Test
    void matchmakingBestOfThreeAndRankingUpdate() throws Exception {
        Profile ana = register("Ana");
        Profile beto = register("Beto");
        a = TestClient.connect(port);
        b = TestClient.connect(port);

        queue(a, ana);
        assertThat(a.expect("QUEUED").get("wins").asInt()).isZero();
        queue(b, beto);

        JsonNode foundA = a.expect("MATCH_FOUND");
        JsonNode foundB = b.expect("MATCH_FOUND");
        assertThat(foundA.at("/room/competitive").asBoolean()).isTrue();
        assertThat(foundA.at("/room/targetWins").asInt()).isEqualTo(2);
        assertThat(foundA.at("/you/id").asText()).isEqualTo(ana.id());
        assertThat(foundB.at("/you/colorHex").asText()).isNotEqualTo(foundA.at("/you/colorHex").asText());

        playRound(a, "ROCK", b, "SCISSORS");
        JsonNode r1 = a.expect("ROUND_RESULT");
        b.expect("ROUND_RESULT");
        assertThat(r1.get("matchOver").asBoolean()).isFalse();
        playRound(a, "PAPER", b, "ROCK");
        JsonNode r2 = b.expect("ROUND_RESULT");
        assertThat(r2.get("matchOver").asBoolean()).isTrue();

        JsonNode resultA = a.expect("MATCH_RESULT");
        JsonNode resultB = b.expect("MATCH_RESULT");
        assertThat(resultA.get("winnerId").asText()).isEqualTo(ana.id());
        assertThat(resultA.get("counted").asBoolean()).isTrue();
        assertThat(resultA.at("/you/wins").asInt()).isEqualTo(1);
        assertThat(resultA.at("/you/position").asLong()).isEqualTo(1);
        assertThat(resultB.at("/you/losses").asInt()).isEqualTo(1);
        assertThat(resultB.at("/you/wins").asInt()).isZero();

        Map<?, ?> lb = rest.getForObject("/api/leaderboard?playerId=" + beto.id(), Map.class);
        assertThat(((java.util.List<?>) lb.get("top"))).hasSize(2);
        assertThat(((Map<?, ?>) lb.get("me")).get("position")).isEqualTo(2);
    }

    @Test
    void disconnectingMidMatchCountsAsForfeit() throws Exception {
        Profile ana = register("Ana");
        Profile beto = register("Beto");
        a = TestClient.connect(port);
        b = TestClient.connect(port);
        queue(a, ana);
        queue(b, beto);
        a.expect("MATCH_FOUND");
        b.expect("MATCH_FOUND");
        playRound(a, "ROCK", b, "SCISSORS"); // Ana gana la primera ronda...
        a.expect("ROUND_RESULT");

        a.session.close(); // ...y cierra el navegador
        JsonNode result = b.expect("MATCH_RESULT");
        assertThat(result.get("forfeit").asBoolean()).isTrue();
        assertThat(result.get("winnerId").asText()).isEqualTo(beto.id());
        assertThat(profiles.findById(ana.id()).orElseThrow().getLosses()).isEqualTo(1);
    }

    @Test
    void sameProfileCannotQueueFromTwoTabs() throws Exception {
        Profile ana = register("Ana");
        a = TestClient.connect(port);
        b = TestClient.connect(port);
        queue(a, ana);
        a.expect("QUEUED");
        queue(b, ana);
        assertThat(b.expect("ERROR").get("reason").asText()).isEqualTo("ALREADY_QUEUED");
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        Profile ana = register("Ana");
        a = TestClient.connect(port);
        queue(a, new Profile(ana.id(), "robado", "Ana"));
        assertThat(a.expect("ERROR").get("reason").asText()).isEqualTo("INVALID_PROFILE");
    }

    @Test
    void pveWinsNeverAffectTheRanking() {
        Profile ana = register("Ana");
        String session = (String) rest.postForEntity("/api/pve/sessions", Map.of("name", "Ana"), Map.class)
                .getBody().get("sessionId");
        for (int i = 0; i < 30; i++) {
            rest.postForEntity("/api/pve/sessions/" + session + "/rounds", Map.of("move", "ROCK"), Map.class);
        }
        var profile = profiles.findById(ana.id()).orElseThrow();
        assertThat(profile.getWins()).isZero();
        assertThat(profile.getLosses()).isZero();
        Map<?, ?> lb = rest.getForObject("/api/leaderboard", Map.class);
        assertThat((java.util.List<?>) lb.get("top")).isEmpty();
    }

    private Profile register(String base) {
        String name = base + UUID.randomUUID().toString().substring(0, 4);
        Map<?, ?> body = rest.postForEntity("/api/players", Map.of("name", name), Map.class).getBody();
        return new Profile((String) body.get("playerId"), (String) body.get("token"), name);
    }

    private static void queue(TestClient c, Profile p) throws Exception {
        c.send("{\"type\":\"QUEUE\",\"playerId\":\"" + p.id() + "\",\"token\":\"" + p.token() + "\"}");
    }

    private static void playRound(TestClient c1, String m1, TestClient c2, String m2) throws Exception {
        c1.send("{\"type\":\"MOVE\",\"move\":\"" + m1 + "\"}");
        c2.send("{\"type\":\"MOVE\",\"move\":\"" + m2 + "\"}");
    }
}
