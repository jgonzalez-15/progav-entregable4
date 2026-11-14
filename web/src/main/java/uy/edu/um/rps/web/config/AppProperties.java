package uy.edu.um.rps.web.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Datos del ambiente en el que corre la app. El pipeline los inyecta como
 * variables de entorno (APP_ENV, APP_VERSION, APP_COMMIT) al desplegar.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("DEV") String env,
        @DefaultValue("local") String version,
        @DefaultValue("unknown") String commit,
        @DefaultValue("30m") Duration idleTimeout) {
}
