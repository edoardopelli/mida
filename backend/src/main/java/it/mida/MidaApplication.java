package it.mida;

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

    public static void main(String[] args) {
        SpringApplication.run(MidaApplication.class, args);
    }
}
