package com.marketing.agencia.Auth.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record SesionRespuesta(
        String tipoToken,
        String token,
        long expiraEn,
        OffsetDateTime fechaExpiracion,
        UsuarioRespuesta usuario,
        List<String> permisos,
        String rutaInicio
) {
}
