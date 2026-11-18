package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Operaciones CRM con columnas permitidas, permisos vivos y control de concurrencia. */
@Service
@RequiredArgsConstructor
public class CrmOperacionServicio {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    private final CrmEvolucionServicio evolucion;
    static final Map<String,String> TABLAS=Map.ofEntries(
        Map.entry("pipeline","crm_oportunidad"),Map.entry("etapas","crm_etapa"),Map.entry("actividades","crm_actividad"),
        Map.entry("segmentos","crm_segmento"),Map.entry("scoring","crm_regla_score"),Map.entry("metas","crm_meta"),
        Map.entry("contenido","crm_contenido"),Map.entry("automatizaciones","crm_automatizacion"),Map.entry("integraciones","crm_integracion_meta"));
    public Map<String,Object> capacidades(UUID sujeto) {
        Map<String,Object> m=new LinkedHashMap<>();
        for(String r:List.of("pipeline","etapas","actividades","segmentos","scoring","atribucion","metas","contenido","automatizaciones","integraciones","auditoria","campos")) {
            for(boolean e:List.of(false,true)) { boolean ok=true;try{acceso.exigir(sujeto,r,e);}catch(ExcepcionApi ex){ok=false;} m.put(r+(e?"Gestionar":"Leer"),ok); }
        }
        return m;
    }
    boolean analista(long usuario) {
        String rol=jdbc.queryForObject("select lower(r.nombre_rol) from public.usuario u join public.rol r using(id_rol) where u.id_usuario=?",String.class,usuario);
        return rol!=null && (rol.contains("analista")||rol.contains("operador"));
    }
    private String tabla(String r) { if(!TABLAS.containsKey(r))throw invalido("Recurso inválido");return "public."+TABLAS.get(r); }
    public Map<String,Object> opciones(UUID sujeto,String recurso) {
        acceso.exigir(sujeto,recurso,false);
        // Los analistas solo reciben contactos y usuarios necesarios para su propio trabajo.
        long usuario=acceso.exigir(sujeto,recurso,false);
        boolean propio=analista(usuario);
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("etapas",jdbc.queryForList("select id,nombre,tipo,orden from public.crm_etapa order by orden"));
        m.put("contactos",jdbc.queryForList("select id,nombre from public.crm_contacto order by nombre limit 1000"));
        m.put("responsables",jdbc.queryForList("select id_usuario id,nombres nombre from public.usuario where upper(estado)='ACTIVO' and (?=false or id_usuario=?) order by nombres limit 1000",propio,usuario));
        m.put("campanas",jdbc.queryForList("select id_campana id,nombre from public.campana order by nombre limit 1000"));
        m.put("segmentos",jdbc.queryForList("select id,nombre from public.crm_segmento order by nombre limit 1000"));
        m.put("cuentas",propio?List.of():jdbc.queryForList("select id,nombre from public.crm_cuenta order by nombre limit 1000"));
        m.put("oportunidades",propio?List.of():jdbc.queryForList("select id,nombre from public.crm_oportunidad order by nombre limit 1000"));
        m.put("usuarioId",usuario);
        return m;
    }
    public Map<String,Object> listar(String r,int pagina,String q,UUID sujeto) {
        return listar(r,pagina,q,false,sujeto);
    }
    public Map<String,Object> listar(String r,int pagina,String q,boolean propias,UUID sujeto) {
        long usuario=acceso.exigir(sujeto,r,false);
        if(pagina<0||pagina>100000||q.length()>160)throw invalido("Página o búsqueda inválida");
        String t=r.equals("auditoria")?"public.crm_auditoria":tabla(r);
        String filtro=r.equals("auditoria")?" where true":" where nombre ilike ? escape '!'";
        List<Object> p=new ArrayList<>();
        if(!r.equals("auditoria"))p.add("%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        if(Set.of("actividades","contenido").contains(r)&&(analista(usuario)||propias)){filtro+=" and responsable_id=?";p.add(usuario);}
        Long total=jdbc.queryForObject("select count(*) from "+t+filtro,Long.class,p.toArray());
        p.add(25);p.add(pagina*25L);
        return Map.of("filas",jdbc.queryForList((r.equals("automatizaciones")?"select *,exists(select 1 from public.crm_flujo f where f.automatizacion_id=public.crm_automatizacion.id) flujo_visual from ":"select * from ")+t+filtro+" order by id desc limit ? offset ?",p.toArray()),"total",total,"pagina",pagina);
    }
    @Transactional
    public Map<String,Object> guardar(String r,Long id,Map<String,Object> datos,UUID sujeto) {
        long autor=acceso.exigir(sujeto,r,true);if(r.equals("integraciones"))throw new ExcepcionApi(HttpStatus.GONE,"CONECTOR_RETIRADO","Gestiona las campañas desde Rendimiento publicitario");String tabla=tabla(r);
        Map<String,Object> anterior=id==null?Map.of():existente(r,id);
        if(r.equals("automatizaciones")&&id!=null&&jdbc.queryForObject("select exists(select 1 from public.crm_flujo where automatizacion_id=?)",Boolean.class,id))throw invalido("Este flujo usa el editor visual; edítalo desde su ficha visual");
        boolean propio=analista(autor);
        if(propio&&id!=null&&numero(anterior,"responsable_id")!=autor)throw prohibido();
        LinkedHashMap<String,Object> d=validar(r,datos,autor);
        if(r.equals("automatizaciones")&&id==null)d.put("autor_id",autor);
        if(r.equals("etapas")&&id!=null&&!Objects.equals(anterior.get("tipo"),d.get("tipo"))&&jdbc.queryForObject("select count(*) from public.crm_oportunidad where etapa_id=?",Long.class,id)>0)throw invalido("No se puede cambiar el tipo de una etapa con oportunidades. Crea otra etapa.");
        if(propio&&Set.of("actividades","contenido").contains(r)&&numero(d,"responsable_id")!=autor)throw prohibido();
        if(r.equals("contenido")) {
            String previo=Objects.toString(anterior.get("estado"),"BORRADOR");String nuevo=d.get("estado").toString();
            if(propio && (Set.of("APROBADO","RECHAZADO").contains(nuevo)||Set.of("APROBADO","EN_REVISION").contains(previo)))throw prohibido();
            if(Set.of("APROBADO","RECHAZADO").contains(nuevo)&&!previo.equals("EN_REVISION"))throw invalido("Envía primero el contenido a revisión");
        }
        if(r.equals("pipeline")) {
            jdbc.queryForList("select set_config('crm.autor_id',?,true)",Long.toString(autor));
            String tipo=jdbc.queryForObject("select tipo from public.crm_etapa where id=?",String.class,d.get("etapa_id"));
            if("PERDIDA".equals(tipo)&&Objects.toString(d.get("motivo_perdida"),"").isBlank())throw invalido("Indica el motivo de pérdida");
            if("GANADA".equals(tipo))d.put("probabilidad",100);
            if("PERDIDA".equals(tipo))d.put("probabilidad",0);
            String tipoAnterior=id==null?null:jdbc.queryForObject("select tipo from public.crm_etapa where id=?",String.class,anterior.get("etapa_id"));
            d.put("cerrada_en","ABIERTA".equals(tipo)?null:anterior.get("cerrada_en")==null||!Objects.equals(tipoAnterior,tipo)?OffsetDateTime.now():anterior.get("cerrada_en"));
            if(id!=null)d.put("updated_at",OffsetDateTime.now());
        }
        String columnas=String.join(",",d.keySet());
        if(id==null) {
            id=jdbc.queryForObject("insert into "+tabla+"("+columnas+") values("+String.join(",",Collections.nCopies(d.size(),"?"))+") returning id",Long.class,d.values().toArray());
        } else {
            List<Object> p=new ArrayList<>(d.values());p.add(id);
            String version=r.equals("etapas")?"":" and version=?";
            if(!r.equals("etapas"))p.add(numero(datos,"version"));
            String sets=d.keySet().stream().map(k->k+"=?").collect(java.util.stream.Collectors.joining(","));
            int n=jdbc.update("update "+tabla+" set "+sets+(r.equals("etapas")?"":",version=version+1")+" where id=?"+version,p.toArray());
            if(n==0)throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","Otra persona modificó el registro. Cierra el formulario y vuelve a abrirlo para actualizar.");
        }
        auditar(r,id,"GUARDAR",autor);
        if(r.equals("pipeline"))jdbc.update("insert into public.crm_evento(contacto_id,tipo,detalle,autor_id) values(?,'OPORTUNIDAD','Oportunidad actualizada',?)",d.get("contacto_id"),autor);
        return Map.of("id",id);
    }
    @Transactional
    public void eliminar(String r,long id,UUID sujeto) {
        long autor=acceso.exigir(sujeto,r,true);var d=existente(r,id);
        if(analista(autor)&&Set.of("actividades","contenido").contains(r)&&numero(d,"responsable_id")!=autor)throw prohibido();
        if(r.equals("contenido")&&!"BORRADOR".equals(d.get("estado")))throw invalido("Solo se eliminan borradores");
        jdbc.update("delete from "+tabla(r)+" where id=?",id);auditar(r,id,"ELIMINAR",autor);
    }
    Map<String,Object> existente(String r,long id) {
        var filas=jdbc.queryForList("select * from "+tabla(r)+" where id=? for update",id);
        if(filas.isEmpty())throw new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_NO_EXISTE","El registro ya no existe");return filas.getFirst();
    }
    private LinkedHashMap<String,Object> validar(String r,Map<String,Object> d,long autor) {
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("nombre",texto(d,"nombre",160,true));
        switch(r) {
            case "etapas" -> {m.put("orden",entero(d,"orden",0,1000));m.put("tipo",opcion(d,"tipo","ABIERTA","GANADA","PERDIDA"));}
            case "pipeline" -> {
                m.put("contacto_id",referencia(d,"contacto_id","crm_contacto",true));m.put("etapa_id",referencia(d,"etapa_id","crm_etapa",true));
                m.put("responsable_id",responsable(d,autor));m.put("valor",decimal(d,"valor"));m.put("moneda",opcion(d,"moneda","PEN","USD","EUR"));
                m.put("probabilidad",entero(d,"probabilidad",0,100));m.put("fecha_cierre",fecha(d,"fecha_cierre"));m.put("motivo_perdida",texto(d,"motivo_perdida",1000,false));
            }
            case "actividades" -> {
                m.put("tipo",opcion(d,"tipo","LLAMADA","REUNION","SEGUIMIENTO","RECORDATORIO"));m.put("estado",opcion(d,"estado","PENDIENTE","COMPLETADA","CANCELADA"));
                m.put("fecha",instante(d,"fecha"));m.put("responsable_id",responsable(d,autor));m.put("descripcion",texto(d,"descripcion",4000,false));
                m.put("contacto_id",referencia(d,"contacto_id","crm_contacto",false));m.put("cuenta_id",referencia(d,"cuenta_id","crm_cuenta",false));m.put("oportunidad_id",referencia(d,"oportunidad_id","crm_oportunidad",false));
                if(java.util.stream.Stream.of("contacto_id","cuenta_id","oportunidad_id").filter(k->m.get(k)!=null).count()!=1)throw invalido("Vincula la actividad a un contacto, cuenta u oportunidad");
                if(analista(autor)&&(m.get("cuenta_id")!=null||m.get("oportunidad_id")!=null))throw prohibido();
            }
            case "segmentos" -> {
                String estado=texto(d,"estado",25,false);if(estado!=null&&!Set.of("NUEVO","CONTACTADO","CALIFICADO","CLIENTE","DESCARTADO").contains(estado))throw invalido("Estado inválido");
                m.put("estado",estado);m.put("etiqueta",texto(d,"etiqueta",120,false));m.put("origen",texto(d,"origen",120,false));
                m.put("etapa_id",referencia(d,"etapa_id","crm_etapa",false));m.put("sin_actividad_dias",vacio(d.get("sin_actividad_dias"))?null:entero(d,"sin_actividad_dias",0,3650));
            }
            case "scoring" -> {m.put("campo",opcion(d,"campo","ESTADO","ETIQUETA","ORIGEN","ACTIVIDAD_COMPLETADA"));m.put("valor",texto(d,"valor",120,true));m.put("puntos",entero(d,"puntos",-1000,1000));}
            case "metas" -> {
                m.put("campana_id",campana(d));m.put("desde",fecha(d,"desde"));m.put("hasta",fecha(d,"hasta"));
                if(((LocalDate)m.get("hasta")).isBefore((LocalDate)m.get("desde")))throw invalido("El período termina antes del inicio");
                m.put("leads",entero(d,"leads",0,10000000));m.put("ingresos",decimal(d,"ingresos"));m.put("moneda",opcion(d,"moneda","PEN","USD","EUR"));
            }
            case "contenido" -> {
                m.put("campana_id",vacio(d.get("campana_id"))?null:campana(d));m.put("responsable_id",responsable(d,autor));
                m.put("estado",opcion(d,"estado","BORRADOR","EN_REVISION","APROBADO","RECHAZADO"));m.put("texto",texto(d,"texto",10000,true));m.put("observacion",texto(d,"observacion",1000,false));
            }
            case "automatizaciones" -> {
                m.put("segmento_id",referencia(d,"segmento_id","crm_segmento",true));m.put("accion",opcion(d,"accion","CREAR_TAREA","EMAIL"));
                m.put("asunto",texto(d,"asunto",160,true));m.put("mensaje",texto(d,"mensaje",10000,false));m.put("activa",Boolean.TRUE.equals(d.get("activa")));
                if("EMAIL".equals(m.get("accion"))&&vacio(m.get("mensaje")))throw invalido("El email necesita un mensaje");
            }
            default -> throw invalido("Recurso inválido");
        }
        return m;
    }
    private long responsable(Map<String,Object>d,long autor){long id=vacio(d.get("responsable_id"))?autor:numero(d,"responsable_id");if(jdbc.queryForObject("select count(*) from public.usuario where id_usuario=? and upper(estado)='ACTIVO'",Long.class,id)==0)throw invalido("Responsable inactivo o inexistente");return id;}
    private Long referencia(Map<String,Object>d,String campo,String tabla,boolean obligatorio){if(vacio(d.get(campo))){if(obligatorio)throw invalido("Selecciona "+campo);return null;}long id=numero(d,campo);if(jdbc.queryForObject("select count(*) from public."+tabla+" where id=?",Long.class,id)==0)throw invalido("Referencia inexistente: "+campo);return id;}
    private long campana(Map<String,Object>d){long id=numero(d,"campana_id");if(jdbc.queryForObject("select count(*) from public.campana where id_campana=?",Long.class,id)==0)throw invalido("Campaña inexistente");return id;}
    public List<Map<String,Object>> miembros(long segmento,int limite,UUID sujeto){acceso.exigir(sujeto,"segmentos",false);return miembrosInternos(segmento,limite);}
    List<Map<String,Object>> miembrosInternos(long segmento,int limite){
        var s=jdbc.queryForList("select * from public.crm_segmento where id=?",segmento);if(s.isEmpty())throw invalido("Segmento inexistente");var d=s.getFirst();
        return jdbc.queryForList("select c.* from public.crm_contacto c where (?::text is null or c.estado=?) and (?::text is null or exists(select 1 from public.crm_contacto_etiqueta ce join public.crm_etiqueta e on e.id=ce.etiqueta_id where ce.contacto_id=c.id and lower(e.nombre)=lower(btrim(?)))) and (?::text is null or c.origen=?) and (?::bigint is null or exists(select 1 from public.crm_oportunidad o where o.contacto_id=c.id and o.etapa_id=?)) and (?::int is null or not exists(select 1 from public.crm_actividad a where a.contacto_id=c.id and a.fecha>now()-(?::int*interval '1 day'))) order by c.id limit ?",
            d.get("estado"),d.get("estado"),d.get("etiqueta"),d.get("etiqueta"),d.get("origen"),d.get("origen"),d.get("etapa_id"),d.get("etapa_id"),d.get("sin_actividad_dias"),d.get("sin_actividad_dias"),limite);
    }
    public Map<String,Object> segmento(long id,int pagina,UUID sujeto){acceso.exigir(sujeto,"segmentos",false);if(pagina<0||pagina>400)throw invalido("Página inválida");var filas=miembrosInternos(id,10001);if(filas.size()>10000)throw invalido("Acota el segmento a 10000 contactos");return Map.of("total",filas.size(),"filas",filas.stream().skip(pagina*25L).limit(25).toList());}
    public byte[] exportarSegmento(long id,UUID sujeto){acceso.exigir(sujeto,"segmentos",false);var miembros=miembrosInternos(id,10001);if(miembros.size()>10000)throw invalido("Acota el segmento a 10000 contactos");StringBuilder b=new StringBuilder("\uFEFFnombre,correo,telefono,estado\r\n");for(var c:miembros)b.append(List.of("nombre","correo","telefono","estado").stream().map(k->CrmServicio.csvCelda(c.get(k))).collect(java.util.stream.Collectors.joining(","))).append("\r\n");return b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    public List<Map<String,Object>> notificaciones(UUID sujeto){long u=acceso.exigir(sujeto,"actividades",false);return jdbc.queryForList("select id,nombre,fecha from public.crm_actividad where responsable_id=? and estado='PENDIENTE' and fecha<=now() order by fecha limit 100",u);}
    public List<Map<String,Object>> calendario(LocalDate desde,LocalDate hasta,boolean propias,UUID sujeto){long u=acceso.exigir(sujeto,"actividades",false);if(hasta.isBefore(desde)||desde.plusDays(42).isBefore(hasta))throw invalido("El calendario admite hasta 42 días");var filas=jdbc.queryForList("select * from public.crm_actividad where (fecha at time zone 'America/Lima')::date between ? and ? and (?=false or responsable_id=?) order by fecha limit 501",desde,hasta,propias||analista(u),u);if(filas.size()>500)throw invalido("Demasiadas actividades: reduce el período o consulta Mis tareas");return filas;}
    public List<Map<String,Object>> alertas(UUID sujeto){acceso.exigir(sujeto,"metas",false);return jdbc.queryForList("select m.*, (select count(*) from public.crm_contacto c where coalesce((select x.campana_id from public.crm_origen x where x.contacto_id=c.id order by x.created_at,x.id limit 1),c.campana_id)=m.campana_id and (c.created_at at time zone 'America/Lima')::date between m.desde and m.hasta) leads_actuales, coalesce((select sum(o.valor) from public.crm_oportunidad o join public.crm_contacto c on c.id=o.contacto_id join public.crm_etapa e on e.id=o.etapa_id where e.tipo='GANADA' and o.moneda=m.moneda and coalesce((select x.campana_id from public.crm_origen x where x.contacto_id=c.id order by x.created_at,x.id limit 1),c.campana_id)=m.campana_id and (o.cerrada_en at time zone 'America/Lima')::date between m.desde and m.hasta),0) ingresos_actuales, greatest(0,least(1,((now() at time zone 'America/Lima')::date-m.desde+1)::numeric/(m.hasta-m.desde+1))) avance_periodo from public.crm_meta m order by m.hasta desc limit 100");}
    public Map<String,Object> score(long contacto,UUID sujeto){acceso.exigir(sujeto,"contactos",false);var contactos=jdbc.queryForList("select * from public.crm_contacto where id=?",contacto);if(contactos.isEmpty())throw invalido("Contacto inexistente");var c=contactos.getFirst();List<Map<String,Object>> aplicadas=new ArrayList<>();int total=0;
        for(var r:jdbc.queryForList("select * from public.crm_regla_score order by id")){
            String valor=r.get("valor").toString();boolean aplica=switch(r.get("campo").toString()){
                case "ESTADO"->valor.equals(c.get("estado"));case "ORIGEN"->valor.equals(c.get("origen"));case "ETIQUETA"->jdbc.queryForObject("select count(*) from public.crm_contacto_etiqueta ce join public.crm_etiqueta e on e.id=ce.etiqueta_id where ce.contacto_id=? and lower(e.nombre)=lower(btrim(?))",Long.class,contacto,valor)>0;
                case "ACTIVIDAD_COMPLETADA"->jdbc.queryForObject("select count(*) from public.crm_actividad where contacto_id=? and estado='COMPLETADA' and tipo=?",Long.class,contacto,valor)>0;default->false;};
            if(aplica){total+=((Number)r.get("puntos")).intValue();aplicadas.add(r);}
        }return Map.of("puntaje",total,"reglas",aplicadas);
    }
    public Map<String,Object> atribucion(LocalDate desde,LocalDate hasta,UUID sujeto){acceso.exigir(sujeto,"atribucion",false);if(hasta.isBefore(desde)||desde.plusYears(5).isBefore(hasta))throw invalido("Período inválido (máximo 5 años)");
        return atribucion(desde,hasta,"PRIMERO",sujeto);
    }
    public Map<String,Object> atribucion(LocalDate desde,LocalDate hasta,String modelo,UUID sujeto){acceso.exigir(sujeto,"atribucion",false);if(hasta.isBefore(desde)||desde.plusYears(5).isBefore(hasta))throw invalido("Período inválido (máximo 5 años)");
        // Primer origen: nunca se multiplica una venta al existir múltiples contactos publicitarios.
        String origen="with origen as (select c.id,coalesce((select x.campana_id from public.crm_origen x where x.contacto_id=c.id order by x.created_at,x.id limit 1),c.campana_id) campana_id,c.created_at from public.crm_contacto c) ";
        var leads=jdbc.queryForList(origen+"select ca.id_campana,ca.nombre,count(c.id) leads from public.campana ca left join origen c on c.campana_id=ca.id_campana and (c.created_at at time zone 'America/Lima')::date between ? and ? group by ca.id_campana,ca.nombre order by ca.nombre",desde,hasta);
        var ingresos=evolucion.ingresos(desde,hasta,modelo);
        leads.add(Map.of("id_campana",0,"nombre","Sin campaña","leads",jdbc.queryForObject(origen+"select count(*) from origen where campana_id is null and (created_at at time zone 'America/Lima')::date between ? and ?",Long.class,desde,hasta)));
        var gastos=gastosCampanas(desde,hasta);
        var embudo=jdbc.queryForList("select e.nombre,e.tipo,count(o.id) oportunidades from public.crm_etapa e left join public.crm_oportunidad o on o.etapa_id=e.id and (o.created_at at time zone 'America/Lima')::date between ? and ? group by e.id order by e.orden",desde,hasta);
        var rendimiento=jdbc.queryForList("select u.nombres responsable,o.moneda,count(*) oportunidades,sum(o.valor) valor,sum(case when e.tipo='GANADA' then o.valor else 0 end) ingresos from public.crm_oportunidad o join public.usuario u on u.id_usuario=o.responsable_id join public.crm_etapa e on e.id=o.etapa_id where (o.created_at at time zone 'America/Lima')::date between ? and ? group by u.id_usuario,u.nombres,o.moneda",desde,hasta);
        var embudoCampanas=jdbc.queryForList("""
            select coalesce((select x.campana_id from public.crm_origen x where x.contacto_id=c.id
                order by x.created_at,x.id limit 1),c.campana_id) campana_id,
              e.nombre,e.tipo,e.orden,count(o.id) oportunidades
            from public.crm_oportunidad o join public.crm_contacto c on c.id=o.contacto_id
              join public.crm_etapa e on e.id=o.etapa_id
            where (o.created_at at time zone 'America/Lima')::date between ? and ?
            group by 1,e.id order by e.orden
            """,desde,hasta);
        return Map.of("campanas",leads,"ingresos",ingresos,"gastos",gastos,"embudo",embudo,"embudoCampanas",embudoCampanas,"responsables",rendimiento,"conversiones",evolucion.conversiones(desde,hasta,sujeto),"modelo","Ingresos: "+modelo+" (interacciones hasta el cierre). Leads: primer origen por creación. Monedas separadas.");
    }
    void auditar(String r,long id,String accion,long autor){jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values(?,?,?,?)",r,id,accion,autor);}
    List<Map<String,Object>> gastosCampanas(LocalDate desde,LocalDate hasta){
        return jdbc.queryForList("""
            with importado as (
              select d.campana_id,d.moneda,m.fecha,sum(m.inversion) importe
              from public.crm_publicidad_demo_metrica m join public.crm_publicidad_demo d on d.id=m.demo_id
              where m.fecha between ? and ? group by d.campana_id,d.moneda,m.fecha
            ), historico as (
              select campana_id,moneda,fecha,importe from public.crm_gasto_meta
              union all select campana_id,moneda,fecha,importe from public.crm_gasto_google
            ), gastos as (
              select * from importado
              union all select h.campana_id,h.moneda,h.fecha,h.importe from historico h
              where h.fecha between ? and ? and not exists(select 1 from importado i where i.campana_id=h.campana_id and i.moneda=h.moneda and i.fecha=h.fecha)
              union all select campana_id,moneda,fecha,importe from public.crm_inversion where not anulado and fecha between ? and ?
            ) select campana_id,moneda,sum(importe) gasto from gastos group by campana_id,moneda
            """,desde,hasta,desde,hasta,desde,hasta);
    }
    static boolean vacio(Object o){return o==null||o.toString().isBlank();}
    static long numero(Map<String,Object>d,String k){try{return new BigDecimal(Objects.toString(d.get(k),"")).longValueExact();}catch(Exception e){throw invalido("Número inválido: "+k);}}
    static int entero(Map<String,Object>d,String k,int min,int max){long v=numero(d,k);if(v<min||v>max)throw invalido("Fuera de rango: "+k);return (int)v;}
    static BigDecimal decimal(Map<String,Object>d,String k){try{BigDecimal n=new BigDecimal(Objects.toString(d.get(k),""));if(n.signum()<0||n.scale()>2||n.precision()>16)throw new IllegalArgumentException();return n;}catch(Exception e){throw invalido("Importe inválido: "+k);}}
    static String texto(Map<String,Object>d,String k,int max,boolean obligatorio){String s=Objects.toString(d.get(k),"").trim();if(s.length()>max||obligatorio&&s.isBlank())throw invalido("Revisa el campo "+k);return s.isBlank()?null:s;}
    static String opcion(Map<String,Object>d,String k,String...opciones){String s=Objects.toString(d.get(k),"");if(!List.of(opciones).contains(s))throw invalido("Opción inválida: "+k);return s;}
    static LocalDate fecha(Map<String,Object>d,String k){try{return LocalDate.parse(d.get(k).toString());}catch(Exception e){throw invalido("Fecha inválida: "+k);}}
    static OffsetDateTime instante(Map<String,Object>d,String k){try{return OffsetDateTime.parse(d.get(k).toString());}catch(Exception e){throw invalido("Fecha y hora inválidas: "+k);}}
    static ExcepcionApi invalido(String s){return new ExcepcionApi(HttpStatus.BAD_REQUEST,"CRM_DATOS_INVALIDOS",s);}
    static ExcepcionApi prohibido(){return new ExcepcionApi(HttpStatus.FORBIDDEN,"CRM_SIN_PERMISO","Solo puedes gestionar tus registros y enviar contenido propio a revisión");}
}
