package uy.edu.um.rps.web.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlayerApiIT {

    @Autowired private TestRestTemplate rest;

    @Test
    void registerProfileAndChangeCelebrationWithToken() {
        String name = "P" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map> created = rest.postForEntity("/api/players", Map.of("name", name, "celebration", "PEACE"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("playerId");
        String token = (String) created.getBody().get("token");
        assertThat(token).hasSizeGreaterThan(30);
        assertThat(created.getBody().get("celebration")).isEqualTo("PEACE");

        // Mismo nombre con otras mayúsculas -> conflicto (anti suplantación)
        assertThat(rest.postForEntity("/api/players", Map.of("name", name.toLowerCase()), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(put(id, "token-falso", "HORNS").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> ok = put(id, token, "HORNS");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("celebration")).isEqualTo("HORNS");
    }

    @Test
    void listsAvailableCelebrations() {
        List<?> list = rest.getForObject("/api/celebrations", List.class);
        assertThat(list).hasSize(8);
    }

    private ResponseEntity<Map> put(String id, String token, String celebration) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Player-Token", token);
        h.set("Content-Type", "application/json");
        return rest.exchange("/api/players/" + id + "/celebration", HttpMethod.PUT,
                new HttpEntity<>(Map.of("celebration", celebration), h), Map.class);
    }
}
