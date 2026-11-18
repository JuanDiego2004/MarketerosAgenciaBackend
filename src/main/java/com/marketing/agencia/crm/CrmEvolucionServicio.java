package com.marketing.agencia.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmEvolucionServicio {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;

    public List<Map<String,Object>> etiquetas(UUID sujeto){acceso.exigir(sujeto,"contactos",false);return jdbc.queryForList("select e.id,e.nombre,e.color,count(ce.contacto_id) contactos from public.crm_etiqueta e left join public.crm_contacto_etiqueta ce on ce.etiqueta_id=e.id group by e.id order by lower(e.nombre) limit 1000");}
    @Transactional
    public Map<String,Object> etiqueta(Map<String,Object>d,UUID sujeto){
        long autor=acceso.exigir(sujeto,"contactos",true);String nombre=texto(d,"nombre",120,true),color=texto(d,"color",7,true);
        if(nombre.contains(",")||!color.matches("#[0-9a-fA-F]{6}"))throw invalido("Usa una etiqueta sin comas y un color hexadecimal");
        Long id=jdbc.queryForObject("insert into public.crm_etiqueta(nombre,color) values(?,?) on conflict(lower(btrim(nombre))) do update set nombre=crm_etiqueta.nombre returning id",Long.class,nombre,color);
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('etiquetas',?,'GUARDAR',?)",id,autor);
        return Map.of("id",id);
    }
    @Transactional
    public void asignarEtiquetas(long contacto,List<Long> ids,UUID sujeto){
        long autor=acceso.exigir(sujeto,"contactos",true);
        if(ids==null||ids.size()>100||ids.stream().anyMatch(Objects::isNull))throw invalido("Selecciona hasta 100 etiquetas");
        exigirContacto(contacto,true);
        List<String> nombres=new ArrayList<>();
        for(long id:new TreeSet<>(ids)){var f=jdbc.queryForList("select nombre from public.crm_etiqueta where id=?",id);if(f.isEmpty())throw invalido("Una etiqueta ya no existe");nombres.add(f.getFirst().get("nombre").toString());}
        String legacy=String.join(",",nombres);if(legacy.length()>500)throw invalido("Las etiquetas seleccionadas superan el límite de 500 caracteres");
        // El trigger actualiza la relación; los clientes anteriores siguen leyendo el mismo campo.
        jdbc.update("update public.crm_contacto set etiquetas=?,updated_at=now() where id=?",legacy,contacto);
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('contactos',?,'ETIQUETAS',?)",contacto,autor);
    }
    public Map<String,Object> relaciones(long contacto,UUID sujeto){
        acceso.exigir(sujeto,"contactos",false);exigirContacto(contacto,false);
        return Map.of("etiquetas",jdbc.queryForList("select e.* from public.crm_etiqueta e join public.crm_contacto_etiqueta ce on ce.etiqueta_id=e.id where ce.contacto_id=? order by e.nombre",contacto),
            "origenes",jdbc.queryForList("select x.id,x.campana_id,ca.nombre campana,x.canal,x.utm_source,x.utm_medium,x.utm_campaign,x.created_at from public.crm_origen x left join public.campana ca on ca.id_campana=x.campana_id where x.contacto_id=? order by x.created_at desc,x.id desc limit 200",contacto),
            "canalesSuprimidos",jdbc.queryForList("select canal from public.crm_baja_canal where contacto_id=? order by canal",String.class,contacto),
            "emailSuprimido",jdbc.queryForObject("select exists(select 1 from public.crm_baja_email where contacto_id=?)",Boolean.class,contacto),"updatedAt",jdbc.queryForObject("select updated_at from public.crm_contacto where id=?",OffsetDateTime.class,contacto));
    }
    @Transactional
    public void origen(long contacto,Map<String,Object>d,UUID sujeto){
        long autor=acceso.exigir(sujeto,"contactos",true);exigirContacto(contacto,true);
        UUID clave;try{clave=UUID.fromString(Objects.toString(d.get("idempotencia"),""));}catch(Exception e){throw invalido("Envía un UUID de idempotencia");}
        Long campana=vacio(d.get("campana_id"))?null:numero(d,"campana_id");
        if(campana!=null&&jdbc.queryForObject("select count(*) from public.campana where id_campana=?",Long.class,campana)==0)throw invalido("Campaña inexistente");
        OffsetDateTime fecha=vacio(d.get("fecha"))?OffsetDateTime.now():instante(d,"fecha");
        OffsetDateTime creacion=jdbc.queryForObject("select created_at from public.crm_contacto where id=?",OffsetDateTime.class,contacto);
        if(fecha.isAfter(OffsetDateTime.now())||fecha.isBefore(creacion))throw invalido("La interacción debe ocurrir entre la creación del contacto y ahora");
        var previas=jdbc.queryForList("select contacto_id from public.crm_origen where idempotencia=?",clave);
        if(!previas.isEmpty()){if(((Number)previas.getFirst().get("contacto_id")).longValue()!=contacto)throw invalido("La clave ya pertenece a otra interacción");return;}
        jdbc.update("insert into public.crm_origen(contacto_id,campana_id,canal,utm_source,utm_medium,utm_campaign,created_at,idempotencia) values(?,?,?,?,?,?,?,?)",contacto,campana,texto(d,"canal",120,true),texto(d,"utm_source",120,false),texto(d,"utm_medium",120,false),texto(d,"utm_campaign",120,false),fecha,clave);
        jdbc.update("insert into public.crm_evento(contacto_id,tipo,detalle,autor_id) values(?,'ORIGEN','Interacción de marketing registrada',?)",contacto,autor);
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('contactos',?,'ORIGEN',?)",contacto,autor);
    }
    void origenInicial(long contacto,Long campana,String canal){
        if(campana==null)return;
        jdbc.update("insert into public.crm_origen(contacto_id,campana_id,canal,created_at,idempotencia) select id,?, ?,created_at,? from public.crm_contacto where id=? and not exists(select 1 from public.crm_origen where contacto_id=?)",campana,canal,UUID.randomUUID(),contacto,contacto);
    }
    private void exigirContacto(long id,boolean bloquear){if(jdbc.queryForList("select id from public.crm_contacto where id=?"+(bloquear?" for update":""),id).isEmpty())throw invalido("Contacto inexistente");}

    public Map<String,Object> conversiones(LocalDate desde,LocalDate hasta,UUID sujeto){
        acceso.exigir(sujeto,"atribucion",false);
        if(hasta.isBefore(desde)||desde.plusYears(5).isBefore(hasta))throw invalido("Período inválido");
        String cohorte="with cohorte as (select o.id from public.crm_oportunidad o where (o.created_at at time zone 'America/Lima')::date between ? and ? and not exists(select 1 from public.crm_transicion t where t.oportunidad_id=o.id and t.linea_base)) ";
        long total=jdbc.queryForObject(cohorte+"select count(*) from cohorte",Long.class,desde,hasta);
        var etapas=jdbc.queryForList(cohorte+"select t.etapa_id,t.etapa_nombre,t.etapa_tipo,count(distinct t.oportunidad_id) alcanzaron from public.crm_transicion t join cohorte c on c.id=t.oportunidad_id where (t.fecha at time zone 'America/Lima')::date<=? and not t.linea_base group by t.etapa_id,t.etapa_nombre,t.etapa_tipo order by min(t.etapa_orden),t.etapa_id",desde,hasta,hasta);
        var pasos=jdbc.queryForList(cohorte+"select t.etapa_anterior_id origen_id,(select e.nombre from public.crm_etapa e where e.id=t.etapa_anterior_id) origen,t.etapa_id destino_id,t.etapa_nombre destino,count(distinct t.oportunidad_id) convirtieron,(select count(distinct p.oportunidad_id) from public.crm_transicion p join cohorte cp on cp.id=p.oportunidad_id where p.etapa_id=t.etapa_anterior_id and (p.fecha at time zone 'America/Lima')::date<=?) base from public.crm_transicion t join cohorte c on c.id=t.oportunidad_id where t.etapa_anterior_id is not null and not t.linea_base and (t.fecha at time zone 'America/Lima')::date<=? group by t.etapa_anterior_id,t.etapa_id,t.etapa_nombre order by t.etapa_anterior_id,t.etapa_id",desde,hasta,hasta,hasta);
        long excluidas=jdbc.queryForObject("select count(*) from public.crm_oportunidad o where (o.created_at at time zone 'America/Lima')::date between ? and ? and exists(select 1 from public.crm_transicion t where t.oportunidad_id=o.id and t.linea_base)",Long.class,desde,hasta);
        return Map.of("cohorte",total,"etapas",etapas,"transiciones",pasos,"sinHistorialCompleto",excluidas,"criterio","Oportunidades creadas en el período, con movimientos observados hasta su último día. Las anteriores a la migración se excluyen; una reapertura no duplica una conversión.");
    }
    List<Map<String,Object>> ingresos(LocalDate desde,LocalDate hasta,String modelo){
        if(!Set.of("PRIMERO","ULTIMO","LINEAL").contains(modelo))throw invalido("Modelo de atribución inválido");
        String sql="""
            with ventas as (
              select o.*,c.campana_id principal from public.crm_oportunidad o
              join public.crm_contacto c on c.id=o.contacto_id join public.crm_etapa e on e.id=o.etapa_id
              where e.tipo='GANADA' and (o.cerrada_en at time zone 'America/Lima')::date between ? and ?
            ), puntos as (
              select v.id oportunidad_id,x.campana_id,x.created_at,x.id origen_id from ventas v
              join public.crm_origen x on x.contacto_id=v.contacto_id and x.campana_id is not null and x.created_at<=v.cerrada_en
              union all
              select v.id,v.principal,v.created_at,0 from ventas v where not exists(
                select 1 from public.crm_origen x where x.contacto_id=v.contacto_id and x.campana_id is not null and x.created_at<=v.cerrada_en)
            ), ordenados as (
              select *,row_number() over(partition by oportunidad_id order by created_at,origen_id) primero,
              row_number() over(partition by oportunidad_id order by created_at desc,origen_id desc) ultimo,
              count(*) over(partition by oportunidad_id) cantidad from puntos
            ), creditos as (
              select *,case when ?='LINEAL' then 1.0/cantidad else 1.0 end peso from ordenados
              where ?='LINEAL' or (?='PRIMERO' and primero=1) or (?='ULTIMO' and ultimo=1)
            ) select p.campana_id,v.moneda,sum(v.valor*p.peso) ingresos,sum(p.peso) ventas
              from creditos p join ventas v on v.id=p.oportunidad_id group by p.campana_id,v.moneda
            """;
        return jdbc.queryForList(sql,desde,hasta,modelo,modelo,modelo,modelo);
    }
}
