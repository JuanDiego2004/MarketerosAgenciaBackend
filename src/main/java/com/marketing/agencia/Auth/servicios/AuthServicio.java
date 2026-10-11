package com.marketing.agencia.Auth.servicios;

import com.marketing.agencia.Auth.dto.response.SesionRespuesta;
import com.marketing.agencia.Auth.dto.response.RespuestaAuthSupabase;
import com.marketing.agencia.Auth.dto.response.UsuarioRespuesta;
import com.marketing.agencia.Auth.mapper.AuthMapper;
import com.marketing.agencia.Auth.mapper.MapeoRespuestaUsuario;
import com.marketing.agencia.Usuario.dto.Requests.LoginRequest;
import com.marketing.agencia.Usuario.dto.Requests.RegistroRequest;
import com.marketing.agencia.Usuario.entidad.Permiso;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServicio {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final AuthMapper authMapper;
    private final MapeoRespuestaUsuario mapeoRespuestaUsuario;
    private final SupabaseAuthServicio supabaseAuthServicio;

    @Transactional
    public SesionRespuesta registro(RegistroRequest request) {
        if (usuarioRepositorio.existsByCorreo(request.getCorreo())) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "CORREO_YA_REGISTRADO",
                    "El correo ya está registrado");
        }

        Rol rol = rolRepositorio.findByNombreRol("ANALISTA")
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.INTERNAL_SERVER_ERROR,
                        "ROL_PREDETERMINADO_NO_CONFIGURADO", "No está configurado el rol inicial"));

        RespuestaAuthSupabase respuestaSupabase = supabaseAuthServicio.registrar(
                request.getCorreo(), request.getPassword());
        UUID idSupabase = respuestaSupabase == null ? null : respuestaSupabase.obtenerIdUsuario();
        if (idSupabase == null) {
            throw new ExcepcionApi(HttpStatus.BAD_GATEWAY, "ERROR_SUPABASE",
                    "Supabase no devolvió el identificador del usuario");
        }

        Usuario usuario = authMapper.toUsuario(request);
        usuario.setPasswordHash(null);
        usuario.setIdSupabase(idSupabase);
        usuario.setEstado("ACTIVO");
        usuario.setDebeCambiarPassword(false);
        usuario.setCreatedAt(OffsetDateTime.now(ZONA_LIMA));
        usuario.setRol(rol);
        usuarioRepositorio.saveAndFlush(usuario);

        return construirSesion(usuario, respuestaSupabase);
    }

    @Transactional(readOnly = true)
    public SesionRespuesta login(LoginRequest request) {
        RespuestaAuthSupabase respuestaSupabase = supabaseAuthServicio.iniciarSesion(
                request.getCorreo(), request.getContrasena());

        UUID idSupabase = respuestaSupabase == null ? null : respuestaSupabase.obtenerIdUsuario();
        if (idSupabase == null) {
            throw new ExcepcionApi(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                    MENSAJE_CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = usuarioRepositorio.findByIdSupabase(idSupabase)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.FORBIDDEN, "USUARIO_SIN_PERFIL",
                        "La cuenta no tiene un perfil de usuario habilitado"));

        if (!"ACTIVO".equalsIgnoreCase(usuario.getEstado())) {
            throw new ExcepcionApi(HttpStatus.FORBIDDEN, "USUARIO_INACTIVO",
                    "La cuenta está inactiva");
        }

        return construirSesion(usuario, respuestaSupabase);
    }

    private SesionRespuesta construirSesion(Usuario usuario, RespuestaAuthSupabase respuestaSupabase) {
        if (respuestaSupabase.getTokenAcceso() == null || respuestaSupabase.getTokenAcceso().isBlank()) {
            throw new ExcepcionApi(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                    MENSAJE_CREDENCIALES_INVALIDAS);
        }

        long expiraEn = respuestaSupabase.getVenceEn() == null || respuestaSupabase.getVenceEn() <= 0
                ? 1800L
                : respuestaSupabase.getVenceEn();
        OffsetDateTime ahora = OffsetDateTime.now(ZONA_LIMA);

        List<String> permisos = usuario.getRol().getPermisos() == null
                ? List.of()
                : usuario.getRol().getPermisos().stream()
                    .map(Permiso::getCodigo)
                    .filter(codigo -> codigo != null && !codigo.isBlank())
                    .sorted()
                    .toList();

        UsuarioRespuesta usuarioRespuesta = mapeoRespuestaUsuario.mapear(usuario);
        return new SesionRespuesta("Bearer", respuestaSupabase.getTokenAcceso(), expiraEn,
                ahora.plusSeconds(expiraEn), usuarioRespuesta, permisos,
                mapeoRespuestaUsuario.rutaInicial(usuario.getRol().getNombreRol()));
    }
}
