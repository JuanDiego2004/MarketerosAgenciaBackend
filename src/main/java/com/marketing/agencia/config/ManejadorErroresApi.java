package com.marketing.agencia.config;

import com.marketing.agencia.Auth.dto.response.ApiRespuesta;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ManejadorErroresApi {

    private static final Logger registro =
            LoggerFactory.getLogger(ManejadorErroresApi.class);

    @ExceptionHandler(ExcepcionApi.class)
    public ResponseEntity<ApiRespuesta<Void>> manejarErrorApi(
            ExcepcionApi excepcion) {

        return ResponseEntity.status(excepcion.getEstadoHttp())
                .body(ApiRespuesta.error(
                        excepcion.getMessage(),
                        excepcion.getCodigo()
                ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiRespuesta<Void>> manejarValidacion(
            Exception excepcion) {

        String mensaje;

        if (excepcion instanceof MissingServletRequestParameterException) {
            mensaje = "Falta un parámetro obligatorio";
        } else if (excepcion instanceof MethodArgumentTypeMismatchException) {
            mensaje = "Uno de los parámetros tiene un formato incorrecto";
        } else if (excepcion instanceof HttpMessageNotReadableException) {
            mensaje = "El cuerpo de la solicitud está vacío o tiene un formato inválido";
        } else {
            mensaje = "Falta un dato obligatorio o tiene un formato inválido";
        }

        return ResponseEntity.badRequest()
                .body(ApiRespuesta.error(
                        mensaje,
                        "VALIDACION_FALLIDA"
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiRespuesta<Void>> manejarMetodoNoPermitido(
            HttpRequestMethodNotSupportedException excepcion) {

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiRespuesta.error(
                        "El método HTTP no está permitido para este endpoint",
                        "METODO_NO_PERMITIDO"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiRespuesta<Void>> manejarErrorInesperado(
            Exception excepcion) {

        registro.error(
                "Error interno al procesar la solicitud",
                excepcion
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiRespuesta.error(
                        "Ocurrió un error interno",
                        "ERROR_INTERNO"
                ));
    }
}