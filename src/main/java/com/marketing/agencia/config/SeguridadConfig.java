package com.marketing.agencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
    @Bean @Order(0)
    public SecurityFilterChain webhookWhatsApp(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/webhooks/whatsapp").csrf(c->c.disable())
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/webhooks/whatsapp").permitAll().requestMatchers(HttpMethod.POST,"/api/webhooks/whatsapp").permitAll().anyRequest().denyAll()).build();
    }
    @Bean @Order(2)
    public SecurityFilterChain bajasPublicas(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/bajas/**").csrf(c->c.disable())
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/bajas/*").permitAll().requestMatchers(HttpMethod.POST,"/api/bajas/*").permitAll().anyRequest().denyAll()).build();
    }
    // Solo el formulario público puede embeberse; el resto conserva las cabeceras de seguridad.
    @Bean @Order(1)
    public SecurityFilterChain captacionPublica(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/captacion/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.disable()).contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors *; base-uri 'none'")))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET,"/api/captacion/*").permitAll().requestMatchers(HttpMethod.POST,"/api/captacion/*","/api/captacion/*/webhook").permitAll().anyRequest().denyAll()).build();
    }

    @Bean
    public CorsConfigurationSource configuracionCORS(
            @Value("${app.cors.allowed-origins}")
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
                        .requestMatchers(HttpMethod.GET,"/actuator/health").permitAll()
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
