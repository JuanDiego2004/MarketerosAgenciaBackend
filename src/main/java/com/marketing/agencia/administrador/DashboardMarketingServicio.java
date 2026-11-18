package com.marketing.agencia.administrador;

import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.marketing.agencia.administrador.DashboardMarketingControlador.*;

@Service
@RequiredArgsConstructor
public class DashboardMarketingServicio {
    private final JdbcTemplate jdbc;
    private final AdministradorServicio acceso;

    @Transactional(readOnly=true)
    public Map<String,Object> obtener(LocalDate desde, LocalDate hasta, UUID usuario) {
        acceso.autorizarDashboard(usuario);
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta)>366)
            throw invalido("Selecciona un período de hasta 367 días");
        Map<String,Object> datos = new LinkedHashMap<>();
        datos.put("campanas", jdbc.queryForList("""
            select c.id_campana as "idCampana",c.nombre,c.estado,c.objetivo,
              c.id_cliente as "idCliente",cl.nombre as cliente,c.id_canal as "idCanal",ca.nombre_canal as canal,
              c.presupuesto_asignado as presupuesto,c.fecha_inicio as "fechaInicio",c.fecha_fin as "fechaFin",
              u.nombres as responsable
            from public.campana c join public.cliente cl using(id_cliente)
              join public.canal_difusion ca using(id_canal)
              left join public.usuario u on u.id_usuario=c.id_usuario_creador
            order by c.created_at desc
            """));
        datos.put("metricas", jdbc.queryForList("""
            select id_campana as "idCampana",fecha,sum(impresiones) impresiones,sum(clics) clics,
              sum(conversiones) conversiones,sum(inversion) inversion,sum(ingresos) ingresos
            from public.metrica_operativa where fecha between ? and ? group by id_campana,fecha order by fecha
            """, desde, hasta));
        boolean geografia = preparada();
        datos.put("geografiaDisponible", geografia);
        datos.put("ubicaciones", geografia ? jdbc.queryForList("""
            select c.campana_id as "idCampana",u.id as "idUbicacion",u.pais,u.codigo_pais as "codigoPais",
              u.ciudad,u.latitud,u.longitud,'COBERTURA' as vista
            from public.marketing_cobertura c join public.marketing_ubicacion u on u.id=c.ubicacion_id
            union
            select m.id_campana,u.id,u.pais,u.codigo_pais,u.ciudad,u.latitud,u.longitud,'RESULTADOS'
            from public.marketing_resultado_geografico r join public.metrica_operativa m on m.id_metrica=r.metrica_id
              join public.marketing_ubicacion u on u.id=r.ubicacion_id where m.fecha between ? and ?
                and (r.impresiones>0 or r.clics>0 or r.conversiones>0)
            """, desde, hasta) : List.of());
        datos.put("resultadosGeograficos", geografia ? jdbc.queryForList("""
            select m.id_campana as "idCampana",r.ubicacion_id as "idUbicacion",m.fecha,
              sum(r.impresiones) impresiones,sum(r.clics) clics,sum(r.conversiones) conversiones,
              sum(r.inversion) inversion,sum(r.ingresos) ingresos
            from public.marketing_resultado_geografico r join public.metrica_operativa m on m.id_metrica=r.metrica_id
            where m.fecha between ? and ? group by m.id_campana,r.ubicacion_id,m.fecha order by m.fecha
            """, desde, hasta) : List.of());
        // Las métricas operativas existentes se registran en PEN en la aplicación.
        datos.put("moneda", "PEN");
        datos.put("fuente", "Métricas operativas registradas · PEN");
        datos.put("desde", desde);
        datos.put("hasta", hasta);
        datos.put("consultadoEn", java.time.Instant.now());
        return datos;
    }

    @Transactional
    public Map<String,Object> cobertura(long campana, Ubicacion u, UUID usuario) {
        acceso.autorizarGeografia(usuario, false);
        exigirGeografia();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.campana where id_campana=?)", Boolean.class, campana)))
            throw invalido("La campaña ya no existe");
        // Una ubicación existente conserva sus coordenadas para las demás campañas.
        long id = Objects.requireNonNull(jdbc.queryForObject("""
            insert into public.marketing_ubicacion(pais,codigo_pais,ciudad,latitud,longitud) values(?,?,?,?,?)
            on conflict(codigo_pais,(lower(ciudad))) do update set codigo_pais=excluded.codigo_pais returning id
            """, Long.class, u.pais().strip(), u.codigoPais().toUpperCase(Locale.ROOT),
                u.ciudad().strip(), u.latitud(), u.longitud()));
        jdbc.update("insert into public.marketing_cobertura(campana_id,ubicacion_id) values(?,?) on conflict do nothing", campana, id);
        return Map.of("idUbicacion", id);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> detalle(long campana, LocalDate desde, LocalDate hasta, UUID usuario) {
        acceso.autorizarDashboard(usuario);
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta)>366)
            throw invalido("Selecciona un período de hasta 367 días");
        exigirGeografia();
        return Map.of("metricas", jdbc.queryForList("""
            select id_metrica as "idMetrica",fecha,impresiones,clics,conversiones,inversion,ingresos
            from public.metrica_operativa where id_campana=? and fecha between ? and ? order by fecha,id_metrica
            """,campana,desde,hasta), "resultadosGeograficos", jdbc.queryForList("""
            select r.metrica_id as "idMetrica",r.ubicacion_id as "idUbicacion",
              r.impresiones,r.clics,r.conversiones,r.inversion,r.ingresos
            from public.marketing_resultado_geografico r join public.metrica_operativa m on m.id_metrica=r.metrica_id
            where m.id_campana=? and m.fecha between ? and ? order by m.fecha,r.ubicacion_id
            """,campana,desde,hasta));
    }

    @Transactional
    public void quitarCobertura(long campana, long ubicacion, UUID usuario) {
        acceso.autorizarGeografia(usuario, false);
        exigirGeografia();
        jdbc.update("delete from public.marketing_cobertura where campana_id=? and ubicacion_id=?", campana, ubicacion);
    }

    @Transactional
    public void resultado(long metrica, long ubicacion, Resultado r, UUID usuario) {
        acceso.autorizarGeografia(usuario, true);
        exigirGeografia();
        List<Map<String,Object>> originales = jdbc.queryForList("select * from public.metrica_operativa where id_metrica=? for update", metrica);
        if (originales.isEmpty()) throw invalido("La métrica ya no existe");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.marketing_ubicacion where id=?)", Boolean.class, ubicacion)))
            throw invalido("La ubicación ya no existe");
        Map<String,Object> usado = jdbc.queryForMap("""
            select coalesce(sum(impresiones),0) impresiones,coalesce(sum(clics),0) clics,
              coalesce(sum(conversiones),0) conversiones,coalesce(sum(inversion),0) inversion,coalesce(sum(ingresos),0) ingresos
            from public.marketing_resultado_geografico where metrica_id=? and ubicacion_id<>?
            """, metrica, ubicacion);
        Map<String,Object> nuevo = Map.of("impresiones",r.impresiones(),"clics",r.clics(),"conversiones",r.conversiones(),"inversion",r.inversion(),"ingresos",r.ingresos());
        for (String campo : nuevo.keySet()) {
            BigDecimal total = new BigDecimal(usado.get(campo).toString()).add(new BigDecimal(nuevo.get(campo).toString()));
            if (total.compareTo(new BigDecimal(originales.getFirst().get(campo).toString()))>0)
                throw invalido("El desglose de "+campo+" supera el total de la métrica original");
        }
        jdbc.update("""
            insert into public.marketing_resultado_geografico(metrica_id,ubicacion_id,impresiones,clics,conversiones,inversion,ingresos)
              values(?,?,?,?,?,?,?) on conflict(metrica_id,ubicacion_id) do update set
              impresiones=excluded.impresiones,clics=excluded.clics,conversiones=excluded.conversiones,
              inversion=excluded.inversion,ingresos=excluded.ingresos
            """, metrica, ubicacion, r.impresiones(),r.clics(),r.conversiones(),r.inversion(),r.ingresos());
    }

    @Transactional
    public void quitarResultado(long metrica, long ubicacion, UUID usuario) {
        acceso.autorizarGeografia(usuario, true);
        exigirGeografia();
        jdbc.queryForList("select id_metrica from public.metrica_operativa where id_metrica=? for update", metrica);
        jdbc.update("delete from public.marketing_resultado_geografico where metrica_id=? and ubicacion_id=?", metrica, ubicacion);
    }

    private boolean preparada() {
        return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('public.marketing_cobertura') is not null and to_regclass('public.marketing_resultado_geografico') is not null", Boolean.class));
    }
    private void exigirGeografia() {
        if (!preparada()) throw invalido("Aplica la migración dashboard-v1.sql para habilitar la geografía");
    }
    private ExcepcionApi invalido(String mensaje) {
        return new ExcepcionApi(HttpStatus.BAD_REQUEST,"DASHBOARD_DATOS_INVALIDOS",mensaje);
    }
}
