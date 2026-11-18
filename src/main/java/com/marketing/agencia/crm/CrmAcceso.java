package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CrmAcceso {
    private final JdbcTemplate jdbc;
    public long exigir(UUID sujeto, String recurso, boolean escritura) {
        if (!Set.of("contactos", "cuentas", "pipeline", "etapas", "actividades", "segmentos", "scoring", "atribucion", "metas", "contenido", "automatizaciones", "integraciones", "auditoria", "campos").contains(recurso)) throw new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_RECURSO_INVALIDO","Recurso CRM inexistente");
        var filas = jdbc.queryForList("select u.id_usuario,r.nombre_rol from public.usuario u join public.rol r using(id_rol) where u.id_supabase=? and upper(u.estado)='ACTIVO'", sujeto);
        if (filas.isEmpty()) throw denegado();
        var fila = filas.getFirst();
        String rol = Normalizer.normalize(fila.get("nombre_rol").toString(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        boolean admin = rol.contains("admin");
        boolean lider = rol.contains("lider") || rol.contains("manager") || rol.contains("lead");
        boolean analista = rol.contains("analista") || rol.contains("operador");
        boolean reservado = Set.of("etapas","integraciones","auditoria","campos").contains(recurso);
        boolean analistaPermitido = analista && (Set.of("actividades","segmentos","contenido").contains(recurso) || !escritura && Set.of("contactos","atribucion").contains(recurso));
        if (!admin && (reservado || !lider && !analistaPermitido)) throw denegado();
        if (escritura && Set.of("atribucion","auditoria").contains(recurso)) throw denegado();
        long usuario = ((Number)fila.get("id_usuario")).longValue();
        String codigo = "CRM_" + recurso.toUpperCase(Locale.ROOT) + (escritura ? "_GESTIONAR" : "_LEER");
        Integer cantidad = jdbc.queryForObject("select count(*) from public.rol_permiso rp join public.permiso p using(id_permiso) join public.usuario u using(id_rol) where u.id_usuario=? and p.codigo=?", Integer.class, usuario, codigo);
        if (!admin && (cantidad == null || cantidad == 0)) throw denegado();
        return usuario;
    }
    private ExcepcionApi denegado() { return new ExcepcionApi(HttpStatus.FORBIDDEN,"CRM_SIN_PERMISO","Tu cuenta no tiene permiso para esta operación CRM"); }
}
