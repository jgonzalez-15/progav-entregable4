package uy.edu.um.rps.web.pve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uy.edu.um.rps.web.history.MatchRecordRepository;

/** Integración completa: HTTP real -> controller -> servicio -> core -> H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PveApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MatchRecordRepository repository;

    @Test
    @SuppressWarnings("unchecked")
    void fullPveMatchKeepsScoreAndPersistsHistory() {
        long before = repository.count();

        ResponseEntity<Map> created = rest.postForEntity("/api/pve/sessions", Map.of("name", "Integración"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("sessionId");
        Map<String, Object> player = (Map<String, Object>) created.getBody().get("player");
        Map<String, Object> cpu = (Map<String, Object>) created.getBody().get("cpu");
        assertThat(player.get("colorHex")).isNotEqualTo(cpu.get("colorHex"));

        Map<String, Object> last = null;
        for (String move : List.of("ROCK", "PAPER", "SCISSORS", "piedra", "tijera")) {
            ResponseEntity<Map> round = rest.postForEntity("/api/pve/sessions/" + id + "/rounds",
                    Map.of("move", move), Map.class);
            assertThat(round.getStatusCode()).isEqualTo(HttpStatus.OK);
            last = round.getBody();
        }

        Map<String, Integer> score = (Map<String, Integer>) last.get("score");
        assertThat(score.get("rounds")).isEqualTo(5);
        assertThat(score.get("player") + score.get("cpu") + score.get("draws")).isEqualTo(5);
        assertThat(repository.count()).isEqualTo(before + 5);

        ResponseEntity<List> history = rest.getForEntity("/api/history?limit=5", List.class);
        assertThat(history.getBody()).hasSize(5);

        rest.delete("/api/pve/sessions/" + id);
        assertThat(rest.getForEntity("/api/pve/sessions/" + id, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidMoveReturns400() {
        String id = (String) rest.postForEntity("/api/pve/sessions", Map.of("name", "Ana"), Map.class)
                .getBody().get("sessionId");
        ResponseEntity<Map> r = rest.postForEntity("/api/pve/sessions/" + id + "/rounds", Map.of("move", "spock"),
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
