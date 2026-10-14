package com.marketing.agencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SeguridadConfig {

    @Bean
    public CorsConfigurationSource configuracionCORS(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
            List<String> origenesPermitidos) {

        CorsConfiguration configuracion = new CorsConfiguration();

        configuracion.setAllowedOrigins(origenesPermitidos);

        configuracion.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuracion.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin"
        ));

        configuracion.setExposedHeaders(List.of(
                "Authorization"
        ));

        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource origen =
                new UrlBasedCorsConfigurationSource();

        origen.registerCorsConfiguration("/**", configuracion);

        return origen;
    }

    @Bean
    public JwtDecoder decodificadorJwt(
            @Value("${supabase.url}") String urlSupabase) {

        String urlNormalizada = urlSupabase.replaceAll("/+$", "");

        return NimbusJwtDecoder
                .withJwkSetUri(
                        urlNormalizada + "/auth/v1/.well-known/jwks.json"
                )
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(
            HttpSecurity seguridadHttp,
            CorsConfigurationSource configuracionCORS,
            JwtDecoder decodificadorJwt) throws Exception {

        return seguridadHttp
                .cors(cors -> cors.configurationSource(configuracionCORS))

                .csrf(csrf -> csrf.disable())

                .sessionManagement(sesion -> sesion
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(autorizaciones -> autorizaciones

                        // Peticiones de preflight de CORS
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // Endpoints públicos de autenticación
                        .requestMatchers(
                                "/auth/**",
                                "/api/auth/**"
                        ).permitAll()

                        // Swagger y documentación, si están instalados
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // El resto de endpoints requiere autenticación
                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(decodificadorJwt)
                        )
                )

                .build();
    }
}
