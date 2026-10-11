package com.marketing.agencia.config;

import com.marketing.agencia.Auth.dto.response.ApiRespuesta;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErroresApi {

    @ExceptionHandler(ExcepcionApi.class)
    public ResponseEntity<ApiRespuesta<Void>> manejarErrorApi(ExcepcionApi excepcion) {
        return ResponseEntity.status(excepcion.getEstadoHttp())
                .body(ApiRespuesta.error(excepcion.getMessage(), excepcion.getCodigo()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiRespuesta<Void>> manejarValidacion(Exception excepcion) {
        String mensaje = excepcion instanceof MethodArgumentNotValidException errorValidacion
                && errorValidacion.getBindingResult().getObjectName().toLowerCase().contains("login")
                ? "Falta correo o contraseña"
                : "Falta un dato obligatorio o tiene un formato inválido";
        return ResponseEntity.badRequest()
                .body(ApiRespuesta.error(mensaje, "VALIDACION_FALLIDA"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiRespuesta<Void>> manejarErrorInesperado(Exception excepcion) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiRespuesta.error("Ocurrió un error interno", "ERROR_INTERNO"));
    }
}
