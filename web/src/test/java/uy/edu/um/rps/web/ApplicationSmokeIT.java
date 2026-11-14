package uy.edu.um.rps.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Verifica que la app arranca con todos los módulos y sirve frontend, health e info. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthIsUp() {
        ResponseEntity<Map> health = rest.getForEntity("/actuator/health", Map.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).containsEntry("status", "UP");
    }

    @Test
    void infoExposesEnvironmentAndVersion() {
        Map<?, ?> info = rest.getForObject("/api/info", Map.class);
        assertThat(info.get("env")).isEqualTo("TEST");
        assertThat(info.get("version")).isEqualTo("test");
    }

    @Test
    void servesThe3dFrontend() {
        ResponseEntity<String> page = rest.getForEntity("/", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Piedra, Papel o Tijera");
        assertThat(rest.getForEntity("/js/main.js", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/vendor/three/three.module.js", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
