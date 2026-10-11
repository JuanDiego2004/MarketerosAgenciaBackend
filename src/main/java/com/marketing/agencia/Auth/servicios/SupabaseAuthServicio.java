package com.marketing.agencia.Auth.servicios;

import com.marketing.agencia.Auth.dto.response.RespuestaAuthSupabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.marketing.agencia.config.ExcepcionApi;

import java.nio.charset.StandardCharsets;

@Service
public class SupabaseAuthServicio {

    private final RestClient clienteSupabase;
    private final boolean claveApiConfigurada;

    public SupabaseAuthServicio(
            @Value("${supabase.url}") String urlSupabase,
            @Value("${supabase.api-key:}") String claveApiSupabase) {
        RestClient.Builder clienteConfigurado = RestClient.builder()
                .baseUrl(urlSupabase)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        this.claveApiConfigurada = claveApiSupabase != null && !claveApiSupabase.isBlank();
        if (claveApiConfigurada) {
            clienteConfigurado.defaultHeader("apikey", claveApiSupabase);
        }
        this.clienteSupabase = clienteConfigurado.build();
    }

    public RespuestaAuthSupabase registrar(String correo, String password) {
        validarClaveApi();
        return clienteSupabase.post()
                .uri("/auth/v1/signup")
                .body(new CredencialesSupabase(correo, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (solicitud, respuesta) -> {
                    String detalle = new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)
                            .toLowerCase();
                    boolean correoDuplicado = detalle.contains("already registered")
                            || detalle.contains("already exists") || respuesta.getStatusCode().value() == 409;
                    throw new ExcepcionApi(correoDuplicado ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
                            correoDuplicado ? "CORREO_YA_REGISTRADO" : "REGISTRO_SUPABASE_FALLIDO",
                            correoDuplicado ? "El correo ya está registrado" : "Supabase no pudo registrar esa cuenta");
                })
                .body(RespuestaAuthSupabase.class);
    }

    public RespuestaAuthSupabase iniciarSesion(String correo, String password) {
        validarClaveApi();
        return clienteSupabase.post()
                .uri("/auth/v1/token?grant_type=password")
                .body(new CredencialesSupabase(correo, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (solicitud, respuesta) -> {
                    respuesta.getBody().readAllBytes();
                    if (respuesta.getStatusCode().is4xxClientError()) {
                        throw new ExcepcionApi(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                                "Correo o contraseña incorrectos");
                    }
                    throw new ExcepcionApi(HttpStatus.BAD_GATEWAY, "SUPABASE_NO_DISPONIBLE",
                            "No fue posible comunicarse con el servicio de autenticación");
                })
                .body(RespuestaAuthSupabase.class);
    }

    private void validarClaveApi() {
        if (!claveApiConfigurada) {
            throw new ExcepcionApi(HttpStatus.SERVICE_UNAVAILABLE, "SUPABASE_NO_CONFIGURADO",
                    "Configura SUPABASE_PUBLISHABLE_KEY para habilitar el registro e inicio de sesión");
        }
    }

    private record CredencialesSupabase(String email, String password) {
    }
}
