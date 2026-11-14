package uy.edu.um.rps.web.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Parámetros del modo competitivo (application.properties: app.competitive.*). */
@ConfigurationProperties(prefix = "app.competitive")
public record CompetitiveProperties(
        @DefaultValue("2") int targetWins,
        @DefaultValue("3") int maxCountedWinsPerOpponentPerDay,
        @DefaultValue("1") int baseTolerance,
        @DefaultValue("2") int toleranceStep,
        @DefaultValue("5s") Duration toleranceStepEvery,
        @DefaultValue("false") boolean demoSeed) {
}
