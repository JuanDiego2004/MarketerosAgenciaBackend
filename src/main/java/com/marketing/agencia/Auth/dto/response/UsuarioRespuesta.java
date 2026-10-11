package com.marketing.agencia.Auth.dto.response;

public record UsuarioRespuesta(
        Long idUsuario,
        String nombres,
        String correo,
        String estado,
        RolRespuesta rol
) {
    public record RolRespuesta(Long idRol, String nombreRol) {
    }
}
