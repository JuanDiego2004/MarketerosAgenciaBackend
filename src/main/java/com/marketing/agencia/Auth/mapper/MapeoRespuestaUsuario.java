package com.marketing.agencia.Auth.mapper;

import com.marketing.agencia.Auth.dto.response.UsuarioCreadoRespuesta;
import com.marketing.agencia.Auth.dto.response.UsuarioRespuesta;
import com.marketing.agencia.Usuario.entidad.Usuario;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class MapeoRespuestaUsuario {

    public UsuarioRespuesta mapear(Usuario usuario) {
        return new UsuarioRespuesta(usuario.getIdUsuario(), usuario.getNombres(), usuario.getCorreo(),
                usuario.getEstado(), mapearRol(usuario));
    }

    public UsuarioCreadoRespuesta mapearCreado(Usuario usuario) {
        return new UsuarioCreadoRespuesta(usuario.getIdUsuario(), usuario.getNombres(), usuario.getCorreo(),
                usuario.getEstado(), mapearRol(usuario), usuario.getCreatedAt());
    }

    public UsuarioRespuesta.RolRespuesta mapearRol(Usuario usuario) {
        return new UsuarioRespuesta.RolRespuesta(usuario.getRol().getIdRol(),
                nombreVisibleDelRol(usuario.getRol().getNombreRol()));
    }

    public String nombreVisibleDelRol(String nombreRol) {
        String normalizado = normalizar(nombreRol);
        if (normalizado.equals("ANALISTA")) return "Analista de Marketing";
        if (normalizado.equals("PROJECT MANAGER")) return "Project Manager";
        if (normalizado.equals("ADMIN") || normalizado.equals("ADMINISTRADOR")) return "Administrador";
        return nombreRol;
    }

    public String rutaInicial(String nombreRol) {
        String normalizado = normalizar(nombreRol);
        if (normalizado.contains("ANALISTA") || normalizado.contains("MARKETING")
                || normalizado.contains("PROJECT MANAGER")) return "/campanas";
        if (normalizado.contains("ADMIN") || normalizado.contains("GERENTE")) return "/usuarios";
        return "/inicio";
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
