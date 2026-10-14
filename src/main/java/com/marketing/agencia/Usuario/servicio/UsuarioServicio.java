package com.marketing.agencia.Usuario.servicio;

import com.marketing.agencia.Auth.dto.response.RespuestaAuthSupabase;
import com.marketing.agencia.Auth.dto.response.UsuarioCreadoRespuesta;
import com.marketing.agencia.Auth.dto.response.UsuarioRespuesta;
import com.marketing.agencia.Auth.mapper.MapeoRespuestaUsuario;
import com.marketing.agencia.Auth.servicios.SupabaseAuthServicio;
import com.marketing.agencia.Usuario.dto.Requests.CrearUsuarioRequest;
import com.marketing.agencia.Usuario.dto.Requests.EditarUsuarioRequest;
import com.marketing.agencia.Usuario.entidad.Rol;
import com.marketing.agencia.Usuario.entidad.Usuario;
import com.marketing.agencia.Usuario.repositorio.RolRepositorio;
import com.marketing.agencia.Usuario.repositorio.UsuarioRepositorio;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final SupabaseAuthServicio supabaseAuthServicio;
    private final MapeoRespuestaUsuario mapeoRespuestaUsuario;

    @Transactional
    public UsuarioCreadoRespuesta crear(
            CrearUsuarioRequest request, UUID idSupabaseSolicitante) {

        validarAdministrador(idSupabaseSolicitante);

        String correo = request.correo().trim().toLowerCase(Locale.ROOT);

        if (usuarioRepositorio.existsByCorreo(correo)) {
            throw new ExcepcionApi(
                    HttpStatus.CONFLICT,
                    "CORREO_YA_REGISTRADO",
                    "El correo ya está registrado"
            );
        }

        Rol rol = rolRepositorio.findById(request.idRol())
                .orElseThrow(() -> new ExcepcionApi(
                        HttpStatus.BAD_REQUEST,
                        "ROL_INVALIDO",
                        "El rol indicado no existe"
                ));

        RespuestaAuthSupabase respuestaSupabase =
                supabaseAuthServicio.registrar(
                        correo, request.contrasenaTemporal()
                );

        if (respuestaSupabase == null
                || respuestaSupabase.obtenerIdUsuario() == null) {
            throw new ExcepcionApi(
                    HttpStatus.BAD_GATEWAY,
                    "ERROR_SUPABASE",
                    "Supabase no devolvió el identificador del usuario"
            );
        }

        Usuario usuario = new Usuario();
        usuario.setNombres(request.nombres().trim());
        usuario.setCorreo(correo);
        usuario.setPasswordHash(null);
        usuario.setIdSupabase(respuestaSupabase.obtenerIdUsuario());
        usuario.setEstado("ACTIVO");
        usuario.setDebeCambiarPassword(true);
        usuario.setCreatedAt(
                OffsetDateTime.now(ZoneId.of("America/Lima"))
        );
        usuario.setRol(rol);

        Usuario guardado = usuarioRepositorio.saveAndFlush(usuario);

        return mapeoRespuestaUsuario.mapearCreado(guardado);
    }

    @Transactional(readOnly = true)
    public List<UsuarioRespuesta> listar(UUID idSupabaseSolicitante) {
        validarAdministrador(idSupabaseSolicitante);

        return usuarioRepositorio.findAll()
                .stream()
                .map(mapeoRespuestaUsuario::mapear)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioRespuesta buscarPorId(
            Long id, UUID idSupabaseSolicitante) {

        validarAdministrador(idSupabaseSolicitante);

        Usuario usuario = buscarUsuario(id);

        return mapeoRespuestaUsuario.mapear(usuario);
    }

    @Transactional
    public UsuarioRespuesta cambiarEstado(
            Long id, boolean activo, UUID idSupabaseSolicitante) {

        Usuario administrador = validarAdministrador(
                idSupabaseSolicitante
        );

        if (administrador.getIdUsuario().equals(id)) {
            throw new ExcepcionApi(
                    HttpStatus.CONFLICT,
                    "NO_PUEDE_CAMBIAR_SU_PROPIO_ESTADO",
                    "No puedes cambiar tu propio estado desde este endpoint"
            );
        }

        Usuario usuario = buscarUsuario(id);
        usuario.setEstado(activo ? "ACTIVO" : "INACTIVO");

        Usuario actualizado = usuarioRepositorio.saveAndFlush(usuario);

        return mapeoRespuestaUsuario.mapear(actualizado);
    }

    @Transactional
    public UsuarioRespuesta actualizar(Long id, EditarUsuarioRequest request, UUID idSupabaseSolicitante) {
        validarAdministrador(idSupabaseSolicitante);
        Usuario usuario = buscarUsuario(id);
        Rol rol = rolRepositorio.findById(request.idRol())
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.BAD_REQUEST, "ROL_INVALIDO", "El rol indicado no existe"));
        usuario.setNombres(request.nombres().trim());
        usuario.setRol(rol);
        return mapeoRespuestaUsuario.mapear(usuarioRepositorio.saveAndFlush(usuario));
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepositorio.findById(id)
                .orElseThrow(() -> new ExcepcionApi(
                        HttpStatus.NOT_FOUND,
                        "USUARIO_NO_ENCONTRADO",
                        "No existe un usuario con el ID indicado"
                ));
    }

    private Usuario validarAdministrador(UUID idSupabase) {
        Usuario solicitante = usuarioRepositorio
                .findByIdSupabase(idSupabase)
                .orElseThrow(() -> new ExcepcionApi(
                        HttpStatus.FORBIDDEN,
                        "USUARIO_SIN_PERFIL",
                        "La cuenta no tiene un perfil habilitado"
                ));

        if (!"ACTIVO".equalsIgnoreCase(solicitante.getEstado())) {
            throw new ExcepcionApi(
                    HttpStatus.FORBIDDEN,
                    "USUARIO_INACTIVO",
                    "La cuenta está inactiva"
            );
        }

        String rol = normalizar(
                solicitante.getRol() == null
                        ? null
                        : solicitante.getRol().getNombreRol()
        );

        if (!rol.equals("ADMIN") && !rol.equals("ADMINISTRADOR")) {
            throw new ExcepcionApi(
                    HttpStatus.FORBIDDEN,
                    "PERMISOS_INSUFICIENTES",
                    "Solo un administrador puede gestionar usuarios"
            );
        }

        return solicitante;
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
