package com.marketing.agencia.administrador;

import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdministradorServicio {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate consultas;

    private static final Map<String, List<String>> ALIAS_PERMISOS = Map.ofEntries(
            Map.entry("DASHBOARD_VER", List.of("DASHBOARD_VER", "DASHBOARD_LEER", "DASHBOARD_CONSULTAR")),
            Map.entry("CAMPANA_CONSULTAR", List.of("CAMPANA_CONSULTAR", "CAMPANA_VER", "CAMPANA_LEER", "CAMPANAS_LEER")),
            Map.entry("CAMPANA_REGISTRAR", List.of("CAMPANA_REGISTRAR", "CAMPANA_CREAR", "CAMPANA_ESCRIBIR", "CAMPANAS_ESCRIBIR")),
            Map.entry("CAMPANA_MODIFICAR", List.of("CAMPANA_MODIFICAR", "CAMPANA_EDITAR", "CAMPANA_ESCRIBIR", "CAMPANAS_ESCRIBIR")),
            Map.entry("AUDIENCIA_CONSULTAR", List.of("AUDIENCIA_CONSULTAR", "AUDIENCIA_VER", "AUDIENCIA_LEER", "AUDIENCIAS_LEER")),
            Map.entry("AUDIENCIA_GESTIONAR", List.of("AUDIENCIA_GESTIONAR", "AUDIENCIA_ESCRIBIR", "AUDIENCIAS_ESCRIBIR", "AUDIENCIA_REGISTRAR")),
            Map.entry("AUDIENCIA_VINCULAR", List.of("AUDIENCIA_VINCULAR", "AUDIENCIA_ESCRIBIR", "AUDIENCIAS_ESCRIBIR")),
            Map.entry("METRICA_CONSULTAR", List.of("METRICA_CONSULTAR", "METRICA_VER", "METRICA_LEER", "METRICAS_VER", "METRICAS_LEER", "INDICADOR_VER", "INDICADORES_LEER")),
            Map.entry("METRICA_REGISTRAR", List.of("METRICA_REGISTRAR", "METRICA_CREAR", "METRICA_ESCRIBIR", "METRICAS_REGISTRAR", "METRICAS_CREAR", "METRICAS_ESCRIBIR")),
            Map.entry("CLIENTE_CONSULTAR", List.of("CLIENTE_CONSULTAR", "CLIENTE_VER", "CLIENTE_LEER", "CLIENTES_LEER", "CATALOGO_GESTIONAR")),
            Map.entry("CANAL_CONSULTAR", List.of("CANAL_CONSULTAR", "CANAL_VER", "CANAL_LEER", "CANALES_LEER", "CATALOGO_GESTIONAR")),
            Map.entry("CLIENTE_GESTIONAR", List.of("CLIENTE_GESTIONAR", "CLIENTE_ESCRIBIR", "CLIENTES_ESCRIBIR", "CATALOGO_GESTIONAR")),
            Map.entry("CANAL_GESTIONAR", List.of("CANAL_GESTIONAR", "CANAL_ESCRIBIR", "CANALES_ESCRIBIR", "CATALOGO_GESTIONAR")),
            Map.entry("COMPARATIVA_VER", List.of("COMPARATIVA_VER", "COMPARATIVA_LEER", "COMPARATIVAS_VER", "COMPARATIVAS_LEER", "ANALISIS_VER", "ANALISIS_COMPARATIVO_VER")),
            Map.entry("REPORTE_GENERAR", List.of("REPORTE_GENERAR", "REPORTE_CREAR", "REPORTE_EXPORTAR", "REPORTES_GENERAR", "REPORTES_EXPORTAR", "REPORTES_ESCRIBIR")),
            Map.entry("KPI_RECALCULAR", List.of("KPI_RECALCULAR", "INDICADORES_RECALCULAR", "METRICAS_RECALCULAR"))
    );

    /**
     * Códigos atómicos preferidos cuando el catálogo los define. Los códigos
     * generales (por ejemplo CAMPANAS_ESCRIBIR) solo sirven como respaldo en
     * instalaciones antiguas que no tienen permisos específicos para la acción.
     */
    private static final Map<String, List<String>> PERMISOS_ESPECIFICOS = Map.ofEntries(
            Map.entry("DASHBOARD_VER", List.of("DASHBOARD_VER", "DASHBOARD_LEER", "DASHBOARD_CONSULTAR")),
            Map.entry("CAMPANA_CONSULTAR", List.of("CAMPANA_CONSULTAR", "CAMPANA_VER", "CAMPANA_LEER", "CAMPANAS_LEER")),
            Map.entry("CAMPANA_REGISTRAR", List.of("CAMPANA_REGISTRAR", "CAMPANA_CREAR")),
            Map.entry("CAMPANA_MODIFICAR", List.of("CAMPANA_MODIFICAR", "CAMPANA_EDITAR")),
            Map.entry("AUDIENCIA_CONSULTAR", List.of("AUDIENCIA_CONSULTAR", "AUDIENCIA_VER", "AUDIENCIA_LEER", "AUDIENCIAS_LEER")),
            Map.entry("AUDIENCIA_GESTIONAR", List.of("AUDIENCIA_GESTIONAR", "AUDIENCIA_EDITAR")),
            Map.entry("AUDIENCIA_VINCULAR", List.of("AUDIENCIA_VINCULAR")),
            Map.entry("METRICA_CONSULTAR", List.of("METRICA_CONSULTAR", "METRICA_VER", "METRICA_LEER", "METRICAS_VER", "METRICAS_LEER", "INDICADOR_VER", "INDICADORES_LEER")),
            Map.entry("METRICA_REGISTRAR", List.of("METRICA_REGISTRAR", "METRICA_CREAR", "METRICAS_REGISTRAR", "METRICAS_CREAR")),
            Map.entry("CLIENTE_CONSULTAR", List.of("CLIENTE_CONSULTAR", "CLIENTE_VER", "CLIENTE_LEER", "CLIENTES_LEER", "CATALOGO_GESTIONAR")),
            Map.entry("CANAL_CONSULTAR", List.of("CANAL_CONSULTAR", "CANAL_VER", "CANAL_LEER", "CANALES_LEER", "CATALOGO_GESTIONAR")),
            Map.entry("CLIENTE_GESTIONAR", List.of("CLIENTE_GESTIONAR", "CLIENTE_ESCRIBIR", "CLIENTES_ESCRIBIR", "CATALOGO_GESTIONAR")),
            Map.entry("CANAL_GESTIONAR", List.of("CANAL_GESTIONAR", "CANAL_ESCRIBIR", "CANALES_ESCRIBIR", "CATALOGO_GESTIONAR")),
            Map.entry("COMPARATIVA_VER", List.of("COMPARATIVA_VER", "COMPARATIVA_LEER", "COMPARATIVAS_VER", "COMPARATIVAS_LEER", "ANALISIS_VER", "ANALISIS_COMPARATIVO_VER")),
            Map.entry("REPORTE_GENERAR", List.of("REPORTE_GENERAR", "REPORTE_CREAR", "REPORTE_EXPORTAR", "REPORTES_GENERAR", "REPORTES_EXPORTAR", "REPORTES_ESCRIBIR")),
            Map.entry("KPI_RECALCULAR", List.of("KPI_RECALCULAR", "INDICADORES_RECALCULAR", "METRICAS_RECALCULAR"))
    );

    @Transactional(readOnly = true)
    public void validarAdministrador(UUID idSupabase) {
        List<String> roles = jdbc.query(
                "select r.nombre_rol from public.usuario u join public.rol r on r.id_rol=u.id_rol where u.id_supabase=? and upper(u.estado)='ACTIVO'",
                (rs, fila) -> rs.getString(1), idSupabase);
        if (roles.isEmpty() || !normalizar(roles.get(0)).contains("ADMIN")) {
            throw new ExcepcionApi(HttpStatus.FORBIDDEN, "PERMISOS_INSUFICIENTES",
                    "Solo un administrador activo puede realizar esta operación");
        }
    }

    @Transactional(readOnly = true)
    private void validarPermiso(UUID idSupabase, String codigoPermiso) {
        validarPermiso(idSupabase, List.of(codigoPermiso));
    }

    @Transactional(readOnly = true)
    private void validarPermiso(UUID idSupabase, List<String> permisosPermitidos) {
        List<String> roles = jdbc.query(
                "select r.nombre_rol from public.usuario u join public.rol r on r.id_rol=u.id_rol where u.id_supabase=? and upper(u.estado)='ACTIVO'",
                (rs, fila) -> rs.getString(1), idSupabase);
        if (roles.isEmpty()) {
            throw new ExcepcionApi(HttpStatus.FORBIDDEN, "USUARIO_SIN_PERFIL", "La cuenta no tiene un perfil activo");
        }
        if (normalizar(roles.getFirst()).contains("ADMIN")) return;

        List<String> codigosCatalogo = jdbc.queryForList(
                "select upper(codigo) from public.permiso", String.class);
        Set<String> codigosDisponibles = new HashSet<>(codigosCatalogo);
        Set<String> codigosAceptados = new LinkedHashSet<>();
        for (String permiso : permisosPermitidos) {
            List<String> especificos = PERMISOS_ESPECIFICOS.get(permiso);
            boolean existenEspecificos = especificos != null
                    && especificos.stream().anyMatch(codigosDisponibles::contains);
            codigosAceptados.addAll(existenEspecificos
                    ? especificos
                    : ALIAS_PERMISOS.getOrDefault(permiso, List.of(permiso)));
        }
        Integer cantidad = consultas.queryForObject(
                "select count(*) from public.usuario u join public.rol_permiso rp on rp.id_rol=u.id_rol join public.permiso p on p.id_permiso=rp.id_permiso where u.id_supabase=:idSupabase and upper(u.estado)='ACTIVO' and upper(p.codigo) in (:permisos)",
                new MapSqlParameterSource().addValue("idSupabase", idSupabase).addValue("permisos", codigosAceptados),
                Integer.class);
        if (cantidad == null || cantidad == 0) {
            throw new ExcepcionApi(HttpStatus.FORBIDDEN, "PERMISOS_INSUFICIENTES",
                    "La cuenta no tiene permiso para realizar esta operación");
        }
    }

    public List<Map<String, Object>> listar(String tabla, UUID solicitante) {
        switch (tabla) {
            case "campanas" -> validarPermiso(solicitante, "CAMPANA_CONSULTAR");
            case "audiencias" -> validarPermiso(solicitante, "AUDIENCIA_CONSULTAR");
            case "metricas" -> validarPermiso(solicitante, "METRICA_CONSULTAR");
            case "clientes" -> validarPermiso(solicitante, "CLIENTE_CONSULTAR");
            case "canales" -> validarPermiso(solicitante, "CANAL_CONSULTAR");
            default -> throw new ExcepcionApi(HttpStatus.NOT_FOUND, "MODULO_NO_ENCONTRADO", "El módulo solicitado no existe");
        }
        String consulta = switch (tabla) {
            case "campanas" -> "select c.id_campana as \"idCampana\",c.nombre,c.id_cliente as \"idCliente\",cl.nombre as cliente,c.id_canal as \"idCanal\",cd.nombre_canal as canal,c.objetivo,c.presupuesto_asignado as \"presupuestoAsignado\",c.fecha_inicio as \"fechaInicio\",c.fecha_fin as \"fechaFin\",c.estado from public.campana c join public.cliente cl using(id_cliente) join public.canal_difusion cd using(id_canal) order by c.created_at desc";
            case "audiencias" -> "select s.id_segmento as \"idSegmento\",s.nombre,s.descripcion,s.criterios,s.edad_min as \"edadMin\",s.edad_max as \"edadMax\",s.ubicacion_geografica as \"ubicacionGeografica\",s.nivel_socioeconomico as \"nivelSocioeconomico\",s.estado,coalesce(string_agg(i.nombre_interes,', '),'') as intereses from public.segmento_audiencia s left join public.segmento_interes si using(id_segmento) left join public.interes i using(id_interes) group by s.id_segmento order by s.created_at desc";
            case "metricas" -> "select m.id_metrica as \"idMetrica\",m.id_campana as \"idCampana\",c.nombre as campana,m.fecha,m.tipo_metrica as \"tipoMetrica\",m.impresiones,m.clics,m.alcance,m.interacciones,m.conversiones,m.inversion,m.ingresos from public.metrica_operativa m join public.campana c using(id_campana) order by m.fecha desc";
            case "clientes" -> "select id_cliente as \"idCliente\",nombre,correo,telefono,estado from public.cliente order by nombre";
            case "canales" -> "select id_canal as \"idCanal\",nombre_canal as \"nombreCanal\",tipo_canal as \"tipoCanal\",estado from public.canal_difusion order by nombre_canal";
            default -> throw new ExcepcionApi(HttpStatus.NOT_FOUND, "MODULO_NO_ENCONTRADO", "El módulo solicitado no existe");
        };
        return jdbc.queryForList(consulta);
    }

    @Transactional
    public Map<String, Object> guardar(String tipo, Long id, Map<String, Object> datos, UUID solicitante) {
        switch (tipo) {
            case "campanas" -> validarPermiso(solicitante, id == null ? "CAMPANA_REGISTRAR" : "CAMPANA_MODIFICAR");
            case "audiencias" -> validarPermiso(solicitante, "AUDIENCIA_GESTIONAR");
            case "metricas" -> {
                if (id == null) validarPermiso(solicitante, "METRICA_REGISTRAR");
                else validarAdministrador(solicitante);
            }
            case "clientes" -> validarPermiso(solicitante, "CLIENTE_GESTIONAR");
            case "canales" -> validarPermiso(solicitante, "CANAL_GESTIONAR");
            default -> throw new ExcepcionApi(HttpStatus.NOT_FOUND, "MODULO_NO_ENCONTRADO", "El módulo solicitado no existe");
        }
        return switch (tipo) {
            case "campanas" -> guardarCampana(id, datos, solicitante);
            case "audiencias" -> guardarAudiencia(id, datos, solicitante);
            case "metricas" -> guardarMetrica(id, datos, solicitante);
            case "clientes" -> guardarCliente(id, datos);
            case "canales" -> guardarCanal(id, datos);
            default -> throw new ExcepcionApi(HttpStatus.NOT_FOUND, "MODULO_NO_ENCONTRADO", "El módulo solicitado no existe");
        };
    }

    @Transactional
    public void eliminar(String tipo, Long id, UUID solicitante) {
        switch (tipo) {
            case "campanas" -> validarPermiso(solicitante, "CAMPANA_MODIFICAR");
            case "audiencias" -> validarPermiso(solicitante, "AUDIENCIA_GESTIONAR");
            case "clientes" -> validarPermiso(solicitante, "CLIENTE_GESTIONAR");
            case "canales" -> validarPermiso(solicitante, "CANAL_GESTIONAR");
            case "metricas" -> validarAdministrador(solicitante);
            default -> throw new ExcepcionApi(HttpStatus.NOT_FOUND, "MODULO_NO_ENCONTRADO", "El módulo solicitado no existe");
        }
        int filas = switch (tipo) {
            case "campanas" -> jdbc.update("update public.campana set estado='FINALIZADA',updated_at=now() where id_campana=?", id);
            case "audiencias" -> jdbc.update("update public.segmento_audiencia set estado='INACTIVO',updated_at=now() where id_segmento=?", id);
            case "clientes" -> jdbc.update("update public.cliente set estado='INACTIVO',updated_at=now() where id_cliente=?", id);
            case "canales" -> jdbc.update("update public.canal_difusion set estado='INACTIVO' where id_canal=?", id);
            case "metricas" -> eliminarMetrica(id);
            default -> throw new ExcepcionApi(HttpStatus.NOT_FOUND, "MODULO_NO_ENCONTRADO", "El módulo solicitado no existe");
        };
        if (filas == 0) throw noEncontrado("registro");
    }

    private int eliminarMetrica(Long id) {
        List<Map<String, Object>> datos = jdbc.queryForList("select id_campana as \"idCampana\",fecha from public.metrica_operativa where id_metrica=?", id);
        if (datos.isEmpty()) return 0;
        Map<String, Object> metrica = datos.get(0);
        int filas = jdbc.update("delete from public.metrica_operativa where id_metrica=?", id);
        recalcularKpi(((Number) metrica.get("idCampana")).longValue(), ((java.sql.Date) metrica.get("fecha")).toLocalDate(), ((java.sql.Date) metrica.get("fecha")).toLocalDate());
        return filas;
    }

    private Map<String, Object> guardarCampana(Long id, Map<String, Object> d, UUID solicitante) {
        long creador = idUsuario(solicitante);
        String nombre = texto(d, "nombre");
        long cliente = numero(d, "idCliente");
        long canal = numero(d, "idCanal");
        String objetivo = texto(d, "objetivo");
        BigDecimal presupuesto = decimal(d, "presupuestoAsignado");
        LocalDate inicio = fecha(d, "fechaInicio"), fin = fecha(d, "fechaFin");
        if (fin.isBefore(inicio) || presupuesto.signum() < 0) throw invalido("Revisa el presupuesto y el rango de fechas");
        String estado = Optional.ofNullable(d.get("estado")).map(Object::toString).orElse("PLANIFICADA").toUpperCase(Locale.ROOT);
        if (!Set.of("PLANIFICADA", "ACTIVA", "PAUSADA", "FINALIZADA").contains(estado)) throw invalido("El estado de campaña no es válido");
        if (id == null) {
            Long nuevo = jdbc.queryForObject("insert into public.campana(nombre,id_cliente,id_canal,objetivo,presupuesto_asignado,fecha_inicio,fecha_fin,estado,id_usuario_creador) values(?,?,?,?,?,?,?,?,?) returning id_campana",
                    Long.class, nombre, cliente, canal, objetivo, presupuesto, inicio, fin, estado, creador);
            return Map.of("idCampana", nuevo, "nombre", nombre, "estado", estado);
        }
        int filas = jdbc.update("update public.campana set nombre=?,id_cliente=?,id_canal=?,objetivo=?,presupuesto_asignado=?,fecha_inicio=?,fecha_fin=?,estado=?,updated_at=now() where id_campana=?",
                nombre, cliente, canal, objetivo, presupuesto, inicio, fin, estado, id);
        if (filas == 0) throw noEncontrado("campaña");
        return Map.of("idCampana", id, "nombre", nombre, "estado", estado);
    }

    private Map<String, Object> guardarAudiencia(Long id, Map<String, Object> d, UUID solicitante) {
        long creador = idUsuario(solicitante);
        String nombre = texto(d, "nombre");
        String estado = Optional.ofNullable(d.get("estado")).map(Object::toString).orElse("ACTIVO").toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVO", "INACTIVO").contains(estado)) throw invalido("El estado de la audiencia no es válido");
        if (id == null) {
            Long nuevo = jdbc.queryForObject("insert into public.segmento_audiencia(nombre,descripcion,criterios,edad_min,edad_max,ubicacion_geografica,nivel_socioeconomico,estado,id_usuario_creador) values(?,?,?,?,?,?,?,?,?) returning id_segmento",
                    Long.class, nombre, d.get("descripcion"), d.get("criterios"), d.get("edadMin"), d.get("edadMax"), d.get("ubicacionGeografica"), d.get("nivelSocioeconomico"), estado, creador);
            actualizarIntereses(nuevo, d.get("intereses"));
            return Map.of("idSegmento", nuevo, "nombre", nombre);
        }
        int filas = jdbc.update("update public.segmento_audiencia set nombre=?,descripcion=?,criterios=?,edad_min=?,edad_max=?,ubicacion_geografica=?,nivel_socioeconomico=?,estado=?,updated_at=now() where id_segmento=?",
                nombre, d.get("descripcion"), d.get("criterios"), d.get("edadMin"), d.get("edadMax"), d.get("ubicacionGeografica"), d.get("nivelSocioeconomico"), estado, id);
        if (filas == 0) throw noEncontrado("audiencia");
        actualizarIntereses(id, d.get("intereses"));
        return Map.of("idSegmento", id, "nombre", nombre);
    }

    private void actualizarIntereses(Long idSegmento, Object valor) {
        if (!(valor instanceof Collection<?> intereses)) return;
        jdbc.update("delete from public.segmento_interes where id_segmento=?", idSegmento);
        for (Object interes : intereses) {
            String nombre = interes.toString().trim();
            if (nombre.isEmpty()) continue;
            jdbc.update("insert into public.interes(nombre_interes) values(?) on conflict(nombre_interes) do nothing", nombre);
            Long idInteres = jdbc.queryForObject("select id_interes from public.interes where nombre_interes=?", Long.class, nombre);
            jdbc.update("insert into public.segmento_interes(id_segmento,id_interes) values(?,?) on conflict do nothing", idSegmento, idInteres);
        }
    }

    private Map<String, Object> guardarMetrica(Long id, Map<String, Object> d, UUID solicitante) {
        long usuario = idUsuario(solicitante), campana = numero(d, "idCampana");
        LocalDate fecha = fecha(d, "fecha");
        String tipo = Optional.ofNullable(d.get("tipoMetrica")).map(Object::toString).orElse("DIARIA").toUpperCase(Locale.ROOT);
        if (!Set.of("DIARIA", "SEMANAL", "MENSUAL").contains(tipo)) throw invalido("El tipo de métrica no es válido");
        long impresiones = entero(d, "impresiones"), clics = entero(d, "clics"), alcance = entero(d, "alcance");
        long interacciones = entero(d, "interacciones"), conversiones = entero(d, "conversiones");
        BigDecimal inversion = decimal(d, "inversion"), ingresos = decimal(d, "ingresos");
        if (List.of(impresiones, clics, alcance, interacciones, conversiones).stream().anyMatch(v -> v < 0) || inversion.signum() < 0 || ingresos.signum() < 0) throw invalido("Las métricas no pueden ser negativas");
        if (id == null) {
            Long nuevo = jdbc.queryForObject("insert into public.metrica_operativa(id_campana,fecha,tipo_metrica,impresiones,clics,alcance,interacciones,conversiones,inversion,ingresos,id_usuario_registro) values(?,?,?,?,?,?,?,?,?,?,?) returning id_metrica",
                    Long.class, campana, fecha, tipo, impresiones, clics, alcance, interacciones, conversiones, inversion, ingresos, usuario);
            recalcularKpi(campana, fecha, fecha);
            return Map.of("idMetrica", nuevo);
        }
        List<Map<String, Object>> metricaAnterior = jdbc.queryForList("select id_campana as \"idCampana\",fecha from public.metrica_operativa where id_metrica=?", id);
        if (metricaAnterior.isEmpty()) throw noEncontrado("métrica");
        int filas = jdbc.update("update public.metrica_operativa set id_campana=?,fecha=?,tipo_metrica=?,impresiones=?,clics=?,alcance=?,interacciones=?,conversiones=?,inversion=?,ingresos=?,updated_at=now() where id_metrica=?",
                campana, fecha, tipo, impresiones, clics, alcance, interacciones, conversiones, inversion, ingresos, id);
        if (filas == 0) throw noEncontrado("métrica");
        Map<String, Object> anterior = metricaAnterior.get(0);
        recalcularKpi(((Number) anterior.get("idCampana")).longValue(), ((java.sql.Date) anterior.get("fecha")).toLocalDate(), ((java.sql.Date) anterior.get("fecha")).toLocalDate());
        recalcularKpi(campana, fecha, fecha);
        return Map.of("idMetrica", id);
    }

    private Map<String, Object> guardarCliente(Long id, Map<String, Object> d) {
        String nombre = texto(d, "nombre");
        if (id == null) {
            Long nuevo = jdbc.queryForObject("insert into public.cliente(nombre,correo,telefono) values(?,?,?) returning id_cliente", Long.class, nombre, d.get("correo"), d.get("telefono"));
            return Map.of("idCliente", nuevo, "nombre", nombre);
        }
        if (jdbc.update("update public.cliente set nombre=?,correo=?,telefono=?,updated_at=now() where id_cliente=?", nombre, d.get("correo"), d.get("telefono"), id) == 0) throw noEncontrado("cliente");
        return Map.of("idCliente", id, "nombre", nombre);
    }

    private Map<String, Object> guardarCanal(Long id, Map<String, Object> d) {
        String nombre = texto(d, "nombreCanal"), tipo = texto(d, "tipoCanal").toUpperCase(Locale.ROOT);
        if (id == null) {
            Long nuevo = jdbc.queryForObject("insert into public.canal_difusion(nombre_canal,tipo_canal) values(?,?) returning id_canal", Long.class, nombre, tipo);
            return Map.of("idCanal", nuevo, "nombreCanal", nombre);
        }
        if (jdbc.update("update public.canal_difusion set nombre_canal=?,tipo_canal=?,estado=? where id_canal=?", nombre, tipo, Optional.ofNullable(d.get("estado")).orElse("ACTIVO"), id) == 0) throw noEncontrado("canal");
        return Map.of("idCanal", id, "nombreCanal", nombre);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID solicitante) {
        validarPermiso(solicitante, "DASHBOARD_VER");
        Map<String, Object> totales = jdbc.queryForMap("select (select count(*) from public.campana where estado='ACTIVA') as \"campanasActivas\",(select count(*) from public.cliente where estado='ACTIVO') as clientes,(select count(*) from public.segmento_audiencia where estado='ACTIVO') as audiencias,coalesce(sum(inversion),0) as inversion,coalesce(sum(ingresos),0) as ingresos,coalesce(sum(impresiones),0) as impresiones,coalesce(sum(clics),0) as clics from public.metrica_operativa");
        totales.put("campanas", jdbc.queryForList("select c.id_campana as \"idCampana\",c.nombre,c.estado,coalesce(sum(m.inversion),0) as inversion,coalesce(sum(m.ingresos),0) as ingresos,coalesce(sum(m.clics),0) as clics,coalesce(sum(m.impresiones),0) as impresiones from public.campana c left join public.metrica_operativa m using(id_campana) group by c.id_campana order by c.created_at desc limit 10"));
        return totales;
    }

    @Transactional
    public Map<String, Object> vincularAudiencia(Long idCampana, Long idSegmento, UUID solicitante) {
        validarPermiso(solicitante, "AUDIENCIA_VINCULAR");
        long usuario = idUsuario(solicitante);
        jdbc.update("insert into public.campana_audiencia(id_campana,id_segmento,id_usuario_vinculo) values(?,?,?) on conflict(id_campana,id_segmento) do nothing", idCampana, idSegmento, usuario);
        return Map.of("idCampana", idCampana, "idSegmento", idSegmento);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audienciasDeCampana(Long idCampana, UUID solicitante) {
        validarPermiso(solicitante, "AUDIENCIA_CONSULTAR");
        return jdbc.queryForList("select s.id_segmento as \"idSegmento\",s.nombre,s.descripcion,s.edad_min as \"edadMin\",s.edad_max as \"edadMax\",s.ubicacion_geografica as \"ubicacionGeografica\" from public.campana_audiencia ca join public.segmento_audiencia s using(id_segmento) where ca.id_campana=? order by s.nombre", idCampana);
    }

    @Transactional
    public Map<String, Object> calcularKpi(Long idCampana, LocalDate desde, LocalDate hasta, UUID solicitante) {
        validarPermiso(solicitante, "KPI_RECALCULAR");
        if (desde == null || hasta == null || hasta.isBefore(desde)) throw invalido("Indica un rango de fechas válido");
        return recalcularKpi(idCampana, desde, hasta);
    }

    private Map<String, Object> recalcularKpi(Long idCampana, LocalDate desde, LocalDate hasta) {
        Map<String, Object> m = jdbc.queryForMap("select coalesce(sum(impresiones),0) impresiones,coalesce(sum(clics),0) clics,coalesce(sum(inversion),0) inversion,coalesce(sum(ingresos),0) ingresos from public.metrica_operativa where id_campana=? and fecha between ? and ?", idCampana, desde, hasta);
        BigDecimal impressions = new BigDecimal(m.get("impresiones").toString());
        BigDecimal clicks = new BigDecimal(m.get("clics").toString());
        BigDecimal spend = new BigDecimal(m.get("inversion").toString());
        BigDecimal revenue = new BigDecimal(m.get("ingresos").toString());
        BigDecimal ctr = ratio(clicks, impressions, 100), cpc = ratio(spend, clicks, 1), cpm = ratio(spend.multiply(BigDecimal.valueOf(1000)), impressions, 1), roi = ratio(revenue.subtract(spend), spend, 100);
        jdbc.update("delete from public.indicador_kpi where id_campana=? and periodo_inicio=? and periodo_fin=?", idCampana, desde, hasta);
        jdbc.update("insert into public.indicador_kpi(id_campana,periodo_inicio,periodo_fin,ctr,cpc,cpm,roi) values(?,?,?,?,?,?,?)", idCampana, desde, hasta, ctr, cpc, cpm, roi);
        return Map.of("idCampana", idCampana, "periodoInicio", desde, "periodoFin", hasta, "ctr", ctr, "cpc", cpc, "cpm", cpm, "roi", roi);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarKpis(UUID solicitante) {
        validarPermiso(solicitante, "METRICA_CONSULTAR");
        return jdbc.queryForList("select k.id_indicador as \"idIndicador\",k.id_campana as \"idCampana\",c.nombre as campana,k.periodo_inicio as \"periodoInicio\",k.periodo_fin as \"periodoFin\",k.ctr,k.cpc,k.cpm,k.roi,k.fecha_calculo as \"fechaCalculo\" from public.indicador_kpi k join public.campana c using(id_campana) order by k.fecha_calculo desc");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> compararCampanas(List<Long> ids, UUID solicitante) {
        validarPermiso(solicitante, "COMPARATIVA_VER");
        if (ids == null || ids.size() < 2) throw invalido("Selecciona al menos dos campañas para comparar");
        return consultas.queryForList("select c.id_campana as \"idCampana\",c.nombre,c.estado,coalesce(sum(m.impresiones),0) as impresiones,coalesce(sum(m.clics),0) as clics,coalesce(sum(m.inversion),0) as inversion,coalesce(sum(m.ingresos),0) as ingresos,case when coalesce(sum(m.impresiones),0)=0 then 0 else round(sum(m.clics)*100.0/sum(m.impresiones),4) end as ctr,case when coalesce(sum(m.inversion),0)=0 then 0 else round((sum(m.ingresos)-sum(m.inversion))*100.0/sum(m.inversion),4) end as roi from public.campana c left join public.metrica_operativa m using(id_campana) where c.id_campana in (:ids) group by c.id_campana order by c.nombre", new MapSqlParameterSource("ids", ids));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> roles(UUID solicitante) {
        validarAdministrador(solicitante);
        List<Map<String, Object>> lista = jdbc.queryForList("select id_rol as \"idRol\",nombre_rol as \"nombreRol\",descripcion from public.rol order by id_rol");
        for (Map<String, Object> rol : lista) {
            rol.put("permisos", jdbc.queryForList("select id_permiso as \"idPermiso\" from public.rol_permiso where id_rol=?", Long.class, rol.get("idRol")));
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> permisos(UUID solicitante) {
        validarAdministrador(solicitante);
        return jdbc.queryForList("select id_permiso as \"idPermiso\",codigo,descripcion,modulo from public.permiso order by modulo,codigo");
    }

    @Transactional
    public void asignarPermisos(Long idRol, List<Long> idPermisos, UUID solicitante) {
        validarAdministrador(solicitante);
        jdbc.update("delete from public.rol_permiso where id_rol=?", idRol);
        if (idPermisos != null) for (Long idPermiso : new LinkedHashSet<>(idPermisos)) {
            jdbc.update("insert into public.rol_permiso(id_rol,id_permiso) values(?,?)", idRol, idPermiso);
        }
    }

    @Transactional
    public byte[] exportarCampanasCsv(LocalDate desde, LocalDate hasta, UUID solicitante) {
        validarPermiso(solicitante, "REPORTE_GENERAR");
        if (desde == null || hasta == null || hasta.isBefore(desde)) throw invalido("Indica un rango de fechas válido");
        List<Map<String, Object>> filas = jdbc.queryForList("select c.nombre,cl.nombre cliente,cd.nombre_canal canal,c.estado,c.presupuesto_asignado presupuesto,c.fecha_inicio,c.fecha_fin,coalesce(sum(m.impresiones),0) impresiones,coalesce(sum(m.clics),0) clics,coalesce(sum(m.inversion),0) inversion,coalesce(sum(m.ingresos),0) ingresos from public.campana c join public.cliente cl using(id_cliente) join public.canal_difusion cd using(id_canal) left join public.metrica_operativa m on m.id_campana=c.id_campana and m.fecha between ? and ? group by c.id_campana,cl.nombre,cd.nombre_canal order by c.nombre", desde, hasta);
        StringBuilder csv = new StringBuilder("Campaña,Cliente,Canal,Estado,Presupuesto,Inicio,Fin,Impresiones,Clics,Inversion,Ingresos\n");
        for (Map<String, Object> fila : filas) csv.append(fila.values().stream().map(v -> "\"" + Objects.toString(v, "").replace("\"", "\"\"") + "\"").reduce((a,b) -> a + "," + b).orElse("")).append('\n');
        jdbc.update("insert into public.reporte(tipo_reporte,fecha_desde,fecha_hasta,formato_exportacion,id_usuario) values('CONSOLIDADO',?,?, 'CSV',?)", desde, hasta, idUsuario(solicitante));
        return ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    }

    private long idUsuario(UUID idSupabase) {
        try { return jdbc.queryForObject("select id_usuario from public.usuario where id_supabase=? and upper(estado)='ACTIVO'", Long.class, idSupabase); }
        catch (Exception e) { throw new ExcepcionApi(HttpStatus.FORBIDDEN, "USUARIO_SIN_PERFIL", "La cuenta no tiene un perfil activo"); }
    }
    private BigDecimal ratio(BigDecimal num, BigDecimal den, int multiplicador) { return den.signum() == 0 ? BigDecimal.ZERO : num.multiply(BigDecimal.valueOf(multiplicador)).divide(den, 4, java.math.RoundingMode.HALF_UP); }
    private String texto(Map<String,Object> d, String campo) { Object v=d.get(campo); if(v==null || v.toString().isBlank()) throw invalido("El campo " + campo + " es obligatorio"); return v.toString().trim(); }
    private long numero(Map<String,Object> d, String campo) { try { return Long.parseLong(Objects.toString(d.get(campo))); } catch(Exception e) { throw invalido("El campo " + campo + " debe ser numérico"); } }
    private long entero(Map<String,Object> d, String campo) { return d.get(campo)==null ? 0 : numero(d,campo); }
    private BigDecimal decimal(Map<String,Object> d, String campo) { try { return new BigDecimal(Objects.toString(d.getOrDefault(campo, "0"))); } catch(Exception e) { throw invalido("El campo " + campo + " debe ser numérico"); } }
    private LocalDate fecha(Map<String,Object> d, String campo) { try { return LocalDate.parse(Objects.toString(d.get(campo))); } catch(Exception e) { throw invalido("El campo " + campo + " debe ser una fecha ISO válida"); } }
    private ExcepcionApi invalido(String mensaje) { return new ExcepcionApi(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", mensaje); }
    private ExcepcionApi noEncontrado(String tipo) { return new ExcepcionApi(HttpStatus.NOT_FOUND, "REGISTRO_NO_ENCONTRADO", "No se encontró la " + tipo + " indicada"); }
    private String normalizar(String texto) { return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT); }
}
