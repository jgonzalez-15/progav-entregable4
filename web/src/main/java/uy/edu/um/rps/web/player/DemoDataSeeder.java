package uy.edu.um.rps.web.player;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.rps.rooms.Celebration;

/**
 * Datos de demostración para UAT: 150 perfiles con estadísticas aleatorias, así QA puede
 * verificar el Top 100 y la posición "fuera del top". Se activa con APP_DEMO_SEED=true y
 * el pipeline solo lo habilita en UAT (nunca en producción).
 */
@Component
@ConditionalOnProperty(name = "app.competitive.demo-seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String[] NAMES = {"Halcon", "Puma", "Yacare", "Carpincho", "Tero", "Hornero", "Zorro",
        "Ñandu", "Lobo", "Ceibo", "Mulita", "Benteveo", "Coati", "Garza", "Lechuza"};

    private final PlayerProfileRepository repository;
    private final Clock clock;

    public DemoDataSeeder(PlayerProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Random r = new Random(2026);
        Instant base = clock.instant().minus(Duration.ofDays(30));
        List<PlayerProfile> bots = new ArrayList<>();
        for (int i = 1; i <= 150; i++) {
            String name = NAMES[i % NAMES.length] + String.format("%03d", i);
            PlayerProfile p = new PlayerProfile(UUID.randomUUID().toString(), name,
                    PlayerService.hash(UUID.randomUUID().toString()), Celebration.values()[i % Celebration.values().length],
                    base);
            int wins = (int) Math.round(Math.pow(r.nextDouble(), 2.2) * 60);
            p.seedStats(wins, r.nextInt(40) + 1, base.plus(Duration.ofMinutes(r.nextInt(40_000))));
            bots.add(p);
        }
        repository.saveAll(bots);
        log.info("Datos de demo: {} perfiles competitivos creados", bots.size());
    }
}
