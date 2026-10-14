package com.marketing.agencia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI configuracionOpenAPI() {
        final String esquemaSeguridad = "Bearer Authentication";

        return new OpenAPI()
                .info(new Info()
                        .title("API Agencia de Marketing")
                        .version("1.0.0")
                        .description("API REST para la gestión de la agencia"))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(esquemaSeguridad))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        esquemaSeguridad,
                                        new SecurityScheme()
                                                .name(esquemaSeguridad)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}