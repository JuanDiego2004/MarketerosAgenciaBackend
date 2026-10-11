package com.marketing.agencia.config;

import org.springframework.http.HttpStatus;

public class ExcepcionApi extends RuntimeException {
    private final HttpStatus estadoHttp;
    private final String codigo;

    public ExcepcionApi(HttpStatus estadoHttp, String codigo, String mensaje) {
        super(mensaje);
        this.estadoHttp = estadoHttp;
        this.codigo = codigo;
    }

    public HttpStatus getEstadoHttp() {
        return estadoHttp;
    }

    public String getCodigo() {
        return codigo;
    }
}
