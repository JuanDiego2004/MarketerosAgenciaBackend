package com.marketing.agencia.Auth.dto.response;

import java.time.OffsetDateTime;

public record UsuarioCreadoRespuesta(
        Long idUsuario,
        String nombres,
        String correo,
        String estado,
        UsuarioRespuesta.RolRespuesta rol,
        OffsetDateTime fechaCreacion
) {
}
