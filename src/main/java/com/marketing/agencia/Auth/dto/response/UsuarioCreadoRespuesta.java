package com.marketing.agencia.auth.dto.response;

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
