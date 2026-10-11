package com.marketing.agencia.Usuario.controlador;

import com.marketing.agencia.Auth.dto.response.ApiRespuesta;
import com.marketing.agencia.Auth.dto.response.UsuarioCreadoRespuesta;
import com.marketing.agencia.Usuario.dto.Requests.CrearUsuarioRequest;
import com.marketing.agencia.Usuario.servicio.UsuarioServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioControlador {
    private final UsuarioServicio usuarioServicio;

    @PostMapping
    public ResponseEntity<ApiRespuesta<UsuarioCreadoRespuesta>> crear(
            @Valid @RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiRespuesta.exito("Usuario creado", usuarioServicio.crear(request)));
    }
}
