package it.mida.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI midaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MIDA API")
                .description("Monitoraggio Integrato dei Doveri Assegnati — API REST, SSE e Web Push")
                .version("1.0.0")
                .contact(new Contact().name("MIDA"))
                .license(new License().name("MIT")));
    }
}
