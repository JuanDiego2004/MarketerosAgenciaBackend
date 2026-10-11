package com.marketing.agencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;

import java.util.List;

@Configuration
public class SeguridadConfig {

    @Bean
    public CorsConfigurationSource configuracionCORS() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", configuracion);
        return origen;
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(
            HttpSecurity seguridadHttp,
            CorsConfigurationSource configuracionCORS,
            @Value("${supabase.url}") String urlSupabase) throws Exception {
        return seguridadHttp
                .cors(cors -> cors.configurationSource(configuracionCORS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(autorizaciones -> autorizaciones
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**", "/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwkSetUri(urlSupabase + "/auth/v1/.well-known/jwks.json")))
                .build();
    }

}
