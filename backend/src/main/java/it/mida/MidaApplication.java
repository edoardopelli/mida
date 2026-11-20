package it.mida;

import it.mida.cluster.MidaRole;
import it.mida.cluster.migration.admin.MigrationAdminApplication;
import it.mida.origin.MidaOriginApplication;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MIDA - Monitoraggio Integrato dei Doveri Assegnati.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class MidaApplication {

    private static volatile boolean mainApplication;

    public static void main(String[] args) {
        mainApplication = true;
        if (!"none".equalsIgnoreCase(configuredMigrationMode(args))) {
            // Migration/rollback/reactivation must never run in the ordinary
            // web application context. The dedicated context has no
            // controllers, schedulers or import/Google/notification beans.
            MigrationAdminApplication.main(args);
            return;
        }
        MidaRole role = configuredRole(args);
        if (role == MidaRole.ORIGIN) {
            MidaOriginApplication.main(args);
            return;
        }
        SpringApplication.run(MidaApplication.class, args);
    }

    static String configuredMigrationMode(String[] args) {
        String argument = Arrays.stream(args)
                .filter(value -> value.startsWith("--mida.cluster.migration.mode=")
                        || value.startsWith("--mida.migration.mode=")
                        || value.startsWith("--MIDA_MIGRATION_MODE="))
                .map(value -> value.substring(value.indexOf('=') + 1))
                .findFirst()
                .orElse(null);
        if (argument != null) {
            return argument.strip();
        }
        String system = System.getProperty("mida.cluster.migration.mode");
        if (system == null || system.isBlank()) {
            system = System.getProperty("mida.migration.mode");
        }
        if (system != null && !system.isBlank()) {
            return system.strip();
        }
        return System.getenv().getOrDefault("MIDA_MIGRATION_MODE", "none").strip();
    }

    /**
     * True only when the process was started through the production main.
     * Test harnesses and embedded starts must not call System.exit when a
     * runtime lease is lost; they use their own explicit shutdown action.
     */
    public static boolean isMainApplication() {
        return mainApplication;
    }

    static MidaRole configuredRole(String[] args) {
        String argument = Arrays.stream(args)
                .filter(value -> value.startsWith("--mida.cluster.role=") || value.startsWith("--MIDA_ROLE="))
                .map(value -> value.substring(value.indexOf('=') + 1))
                .findFirst()
                .orElse(null);
        if (argument != null) {
            return MidaRole.parse(argument);
        }
        String system = System.getProperty("mida.cluster.role");
        if (system != null && !system.isBlank()) {
            return MidaRole.parse(system);
        }
        return MidaRole.parse(System.getenv().getOrDefault("MIDA_ROLE", "standalone"));
    }
}
