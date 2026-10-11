package com.marketing.agencia.Auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiRespuesta<T>(
        boolean exito,
        String mensaje,
        T datos,
        ErrorApi error,
        OffsetDateTime timestamp
) {
    public static <T> ApiRespuesta<T> exito(String mensaje, T datos) {
        return new ApiRespuesta<>(true, mensaje, datos, null, ahoraEnLima());
    }

    public static ApiRespuesta<Void> error(String mensaje, String codigo) {
        return new ApiRespuesta<>(false, mensaje, null, new ErrorApi(codigo, mensaje), ahoraEnLima());
    }

    private static OffsetDateTime ahoraEnLima() {
        return OffsetDateTime.now(ZoneId.of("America/Lima"));
    }

    public record ErrorApi(String codigo, String mensaje) {
    }
}
