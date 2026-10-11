package com.marketing.agencia.Auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespuestaAuthSupabase {

    private UUID id;
    private String email;

    @JsonProperty("access_token")
    private String tokenAcceso;

    @JsonProperty("refresh_token")
    private String tokenRenovacion;

    @JsonProperty("expires_in")
    private Long venceEn;

    private UsuarioSupabase user;

    public UUID obtenerIdUsuario() {
        return id != null ? id : user != null ? user.getId() : null;
    }

    public String obtenerCorreo() {
        return email != null ? email : user != null ? user.getEmail() : null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsuarioSupabase {
        private UUID id;
        private String email;
    }
}
