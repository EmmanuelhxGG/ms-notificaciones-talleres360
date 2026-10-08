package com.talleres360.notificaciones.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentacionApi {
  @Bean
  OpenAPI documentacion() {
    return new OpenAPI()
        .info(new Info().title("Talleres360 — notificaciones").version("1.0"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "claveInterna",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Internal-Key")))
        .addSecurityItem(new SecurityRequirement().addList("claveInterna"));
  }
}
