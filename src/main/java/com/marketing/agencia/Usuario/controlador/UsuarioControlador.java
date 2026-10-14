
package com.marketing.agencia.Usuario.controlador;

import com.marketing.agencia.Auth.dto.response.ApiRespuesta;
import com.marketing.agencia.Auth.dto.response.UsuarioCreadoRespuesta;
import com.marketing.agencia.Auth.dto.response.UsuarioRespuesta;
import com.marketing.agencia.Usuario.dto.Requests.CrearUsuarioRequest;
import com.marketing.agencia.Usuario.servicio.UsuarioServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioControlador {

    private final UsuarioServicio usuarioServicio;

    @PostMapping
    public ResponseEntity<ApiRespuesta<UsuarioCreadoRespuesta>> crear(
            @Valid @RequestBody CrearUsuarioRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UsuarioCreadoRespuesta respuesta = usuarioServicio.crear(
                request, obtenerIdSupabase(jwt)
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiRespuesta.exito("Usuario creado", respuesta));
    }

    @GetMapping
    public ResponseEntity<ApiRespuesta<List<UsuarioRespuesta>>> listar(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                ApiRespuesta.exito(
                        "Usuarios obtenidos",
                        usuarioServicio.listar(obtenerIdSupabase(jwt))
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiRespuesta<UsuarioRespuesta>> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                ApiRespuesta.exito(
                        "Usuario obtenido",
                        usuarioServicio.buscarPorId(
                                id, obtenerIdSupabase(jwt)
                        )
                )
        );
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<ApiRespuesta<UsuarioRespuesta>> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                ApiRespuesta.exito(
                        activo ? "Usuario activado" : "Usuario desactivado",
                        usuarioServicio.cambiarEstado(
                                id, activo, obtenerIdSupabase(jwt)
                        )
                )
        );
    }

    private UUID obtenerIdSupabase(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
