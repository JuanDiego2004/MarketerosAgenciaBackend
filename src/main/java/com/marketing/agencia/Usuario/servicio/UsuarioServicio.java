package com.marketing.agencia.Usuario.servicio;

import com.marketing.agencia.Auth.dto.response.RespuestaAuthSupabase;
import com.marketing.agencia.Auth.dto.response.UsuarioCreadoRespuesta;
import com.marketing.agencia.Auth.mapper.MapeoRespuestaUsuario;
import com.marketing.agencia.Auth.servicios.SupabaseAuthServicio;
import com.marketing.agencia.Usuario.dto.Requests.CrearUsuarioRequest;
import com.marketing.agencia.Usuario.entidad.Rol;
import com.marketing.agencia.Usuario.entidad.Usuario;
import com.marketing.agencia.Usuario.repositorio.RolRepositorio;
import com.marketing.agencia.Usuario.repositorio.UsuarioRepositorio;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class UsuarioServicio {
    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final SupabaseAuthServicio supabaseAuthServicio;
    private final MapeoRespuestaUsuario mapeoRespuestaUsuario;

    @Transactional
    public UsuarioCreadoRespuesta crear(CrearUsuarioRequest request) {
        String correo = request.correo().trim().toLowerCase(java.util.Locale.ROOT);
        if (usuarioRepositorio.existsByCorreo(correo)) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "CORREO_YA_REGISTRADO",
                    "El correo ya está registrado");
        }

        Rol rol = rolRepositorio.findById(request.idRol())
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.BAD_REQUEST, "ROL_INVALIDO",
                        "El rol indicado no existe"));

        RespuestaAuthSupabase respuestaSupabase = supabaseAuthServicio.registrar(
                correo, request.contrasenaTemporal());
        if (respuestaSupabase == null || respuestaSupabase.obtenerIdUsuario() == null) {
            throw new ExcepcionApi(HttpStatus.BAD_GATEWAY, "ERROR_SUPABASE",
                    "Supabase no devolvió el identificador del usuario");
        }

        Usuario usuario = new Usuario();
        usuario.setNombres(request.nombres().trim());
        usuario.setCorreo(correo);
        usuario.setPasswordHash(null);
        usuario.setIdSupabase(respuestaSupabase.obtenerIdUsuario());
        usuario.setEstado("ACTIVO");
        usuario.setDebeCambiarPassword(true);
        usuario.setCreatedAt(OffsetDateTime.now(ZoneId.of("America/Lima")));
        usuario.setRol(rol);

        Usuario guardado = usuarioRepositorio.saveAndFlush(usuario);
        return mapeoRespuestaUsuario.mapearCreado(guardado);
    }
}
