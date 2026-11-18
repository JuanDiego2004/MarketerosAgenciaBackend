package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmFlujosServicio {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    private final CrmOperacionServicio operaciones;
    private final CrmCorreoServicio correo;
    private final CrmMensajeriaServicio mensajeria;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public record Nodo(String id,String tipo,String nombre,Map<String,Object>parametros,String siguiente,String si,String no){}
    public record Definicion(String inicio,List<Nodo>nodos){}
    public record Edicion(String nombre,long segmentoId,boolean activa,int version,Definicion definicion){}
    record Paso(long ejecucion,long contacto,Nodo nodo,boolean continuar){}
    public Map<String,Object> obtener(long id,UUID sujeto){
        acceso.exigir(sujeto,"automatizaciones",false);var a=base(id);var f=jdbc.queryForList("select definicion::text from public.crm_flujo where automatizacion_id=?",id);
        Definicion def=f.isEmpty()?new Definicion("inicio",List.of(new Nodo("inicio","SEGMENTO","Entrada al segmento",Map.of(),"accion",null,null),new Nodo("accion","EMAIL".equals(a.get("accion"))?"EMAIL":"TAREA",a.get("asunto").toString(),Map.of("asunto",a.get("asunto"),"mensaje",Objects.toString(a.get("mensaje"),"")),null,null,null))):leer(f.getFirst().get("definicion").toString());
        return Map.of("nombre",a.get("nombre"),"segmentoId",a.get("segmento_id"),"activa",a.get("activa"),"version",a.get("version"),"definicion",def,"visual",!f.isEmpty());
    }
    @Transactional public void guardar(long id,Edicion d,UUID sujeto){
        long autor=acceso.exigir(sujeto,"automatizaciones",true);var a=jdbc.queryForList("select * from public.crm_automatizacion where id=? for update",id);if(a.isEmpty())throw invalido("Automatización inexistente");
        validar(d.definicion());if(d.nombre()==null||d.nombre().isBlank()||d.nombre().length()>160)throw invalido("Escribe el nombre del flujo");
        if(jdbc.queryForObject("select count(*) from public.crm_segmento where id=?",Long.class,d.segmentoId())==0)throw invalido("Selecciona un segmento existente");
        if(jdbc.update("update public.crm_automatizacion set nombre=?,segmento_id=?,activa=?,version=version+1 where id=? and version=?",d.nombre().trim(),d.segmentoId(),d.activa(),id,d.version())==0)throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","El flujo cambió. Recarga antes de guardar.");
        jdbc.update("insert into public.crm_flujo(automatizacion_id,definicion) values(?,?::jsonb) on conflict(automatizacion_id) do update set definicion=excluded.definicion",id,escribir(d.definicion()));
        operaciones.auditar("automatizaciones",id,"FLUJO_VISUAL",autor);
    }
    static void validar(Definicion d){
        if(d==null||d.nodos()==null||d.nodos().isEmpty()||d.nodos().size()>20)throw invalido("El flujo admite entre 1 y 20 nodos");
        Map<String,Nodo>mapa=new LinkedHashMap<>();int entradas=0,acciones=0;
        for(Nodo n:d.nodos()){
            if(n==null||n.id()==null||!n.id().matches("[a-zA-Z0-9_-]{1,50}")||mapa.put(n.id(),n)!=null)throw invalido("Los nodos necesitan identificadores únicos");
            if(n.nombre()==null||n.nombre().isBlank()||n.nombre().length()>160)throw invalido("Cada nodo necesita un nombre");
            Map<String,Object>p=n.parametros()==null?Map.of():n.parametros();
            switch(Objects.toString(n.tipo(),"")){
                case "SEGMENTO"->entradas++;
                case "TAREA","EMAIL"->{acciones++;texto(p,"asunto",160,true);texto(p,"mensaje",n.tipo().equals("EMAIL")?10000:4000,n.tipo().equals("EMAIL"));}
                case "WHATSAPP","SMS"->{acciones++;CrmMensajeriaServicio.validar(n.tipo(),p);}
                case "ESPERA"->{entero(p,"minutos",1,10080);if(vacio(n.siguiente()))throw invalido("Una espera necesita un paso siguiente");}
                case "CONDICION"->{opcion(p,"campo","ESTADO","ETIQUETA");texto(p,"valor",120,true);}
                default->throw invalido("Tipo de nodo no admitido");
            }
        }
        if(entradas!=1||acciones==0||!mapa.containsKey(d.inicio())||!"SEGMENTO".equals(mapa.get(d.inicio()).tipo()))throw invalido("El flujo necesita una entrada de segmento y al menos una acción");
        Set<String>visitados=new HashSet<>(),ruta=new HashSet<>();visitar(d.inicio(),mapa,visitados,ruta);
        if(visitados.size()!=mapa.size())throw invalido("Conecta todos los nodos con la entrada");
    }
    private static void visitar(String id,Map<String,Nodo>mapa,Set<String>visitados,Set<String>ruta){
        if(id==null||id.isBlank())return;if(!mapa.containsKey(id))throw invalido("Una conexión apunta a un nodo inexistente");if(ruta.contains(id))throw invalido("El flujo no puede contener ciclos");if(visitados.contains(id))return;
        ruta.add(id);Nodo n=mapa.get(id);for(String destino:salidas(n))visitar(destino,mapa,visitados,ruta);ruta.remove(id);visitados.add(id);
    }
    private static List<String>salidas(Nodo n){List<String>r=new ArrayList<>();if("CONDICION".equals(n.tipo())){if(n.si()!=null)r.add(n.si());if(n.no()!=null)r.add(n.no());}else if(n.siguiente()!=null)r.add(n.siguiente());return r;}
    public boolean definida(long id){return jdbc.queryForObject("select exists(select 1 from public.crm_flujo where automatizacion_id=?)",Boolean.class,id);}
    public Map<String,Object> ejecutar(Map<String,Object>a){
        long id=numero(a,"id");Definicion def=leer(jdbc.queryForObject("select definicion::text from public.crm_flujo where automatizacion_id=?",String.class,id));validar(def);
        if(def.nodos().stream().anyMatch(n->n.tipo().equals("EMAIL"))&&!correo.configurado())throw invalido("Configura SMTP y la URL HTTPS de baja antes de ejecutar este flujo");
        for(String canal:List.of("WHATSAPP","SMS"))if(def.nodos().stream().anyMatch(n->n.tipo().equals(canal))&&!mensajeria.configurado(canal))throw invalido("Configura "+canal+" y la URL HTTPS de baja antes de ejecutar este flujo");
        var miembros=operaciones.miembrosInternos(numero(a,"segmento_id"),501);if(miembros.size()>500)throw invalido("Acota el segmento a 500 contactos por flujo");
        for(var c:miembros)jdbc.update("insert into public.crm_ejecucion(automatizacion_id,contacto_id,estado,definicion_flujo,paso_id) values(?,?,'PENDIENTE',?::jsonb,?) on conflict do nothing",id,c.get("id"),escribir(def),def.inicio());
        int pasos=0;
        for(var e:jdbc.queryForList("select id from public.crm_ejecucion where automatizacion_id=? and estado='PENDIENTE' and definicion_flujo is not null and (reanudar_en is null or reanudar_en<=now()) order by id limit 500",id)){
            long ejecucion=numero(e,"id");
            for(int i=0;i<20;i++){
                Paso p=tx.execute(s->avanzar(ejecucion));if(p==null)break;pasos++;
                if(p.nodo()!=null&&Set.of("EMAIL","WHATSAPP","SMS").contains(p.nodo().tipo())){
                    try{boolean enviado=p.nodo().tipo().equals("EMAIL")?correo.enviar(p.contacto(),p.nodo().parametros().get("asunto").toString(),p.nodo().parametros().get("mensaje").toString()):mensajeria.enviar(p.ejecucion(),p.contacto(),p.nodo());tx.executeWithoutResult(s->{log(p.ejecucion(),p.nodo(),enviado?"COMPLETADO":"OMITIDO",enviado?"Aceptado por proveedor; entrega no verificada":"Sin consentimiento vigente");siguiente(p.ejecucion(),p.nodo().siguiente());});}
                    catch(Exception ex){jdbc.update("update public.crm_ejecucion set detalle='Resultado de comunicación incierto; revisar proveedor. No se reintenta automáticamente.' where id=? and estado='INCIERTO'",ejecucion);break;}
                }
                if(!p.continuar())break;
            }
        }
        return Map.of("pasosProcesados",pasos,"estadisticas",jdbc.queryForList("select estado,count(*) cantidad from public.crm_ejecucion where automatizacion_id=? group by estado",id));
    }
    private Paso avanzar(long id){
        var filas=jdbc.queryForList("select id,contacto_id,paso_id,definicion_flujo::text from public.crm_ejecucion where id=? and estado='PENDIENTE' and (reanudar_en is null or reanudar_en<=now()) for update skip locked",id);if(filas.isEmpty())return null;var e=filas.getFirst();long contacto=numero(e,"contacto_id");
        Definicion def=leer(e.get("definicion_flujo").toString());Nodo n=def.nodos().stream().filter(x->x.id().equals(e.get("paso_id"))).findFirst().orElseThrow();Map<String,Object>p=n.parametros()==null?Map.of():n.parametros();
        switch(n.tipo()){
            case "WHATSAPP","SMS"->{if(!mensajeria.consentido(contacto,n.tipo())){log(id,n,"OMITIDO","Sin consentimiento del canal o teléfono internacional válido");siguiente(id,n.siguiente());return new Paso(id,contacto,null,true);}jdbc.update("update public.crm_ejecucion set estado='INCIERTO',detalle='Envío reservado; no reenviar' where id=?",id);log(id,n,"INCIERTO","Envío reservado");return new Paso(id,contacto,n,true);}
            case "EMAIL"->{if(!correo.consentido(contacto)){log(id,n,"OMITIDO","Sin consentimiento de marketing/email");siguiente(id,n.siguiente());return new Paso(id,contacto,null,true);}jdbc.update("update public.crm_ejecucion set estado='INCIERTO',detalle='Envío de email reservado; no reenviar' where id=?",id);log(id,n,"INCIERTO","Envío reservado");return new Paso(id,contacto,n,true);}
            case "TAREA"->{
                long activos=jdbc.queryForObject("select count(*) from public.crm_contacto c join public.usuario u on u.id_usuario=c.responsable_id where c.id=? and upper(u.estado)='ACTIVO'",Long.class,contacto);
                if(activos==0){jdbc.update("update public.crm_ejecucion set estado='ERROR',detalle='El responsable del contacto está inactivo' where id=?",id);log(id,n,"ERROR","Responsable inactivo");return null;}
                jdbc.update("insert into public.crm_actividad(nombre,tipo,estado,fecha,responsable_id,contacto_id,descripcion) select ?,'SEGUIMIENTO','PENDIENTE',now()+interval '1 day',responsable_id,id,? from public.crm_contacto where id=?",p.get("asunto"),p.get("mensaje"),contacto);log(id,n,"COMPLETADO","Tarea de seguimiento creada");siguiente(id,n.siguiente());}
            case "ESPERA"->{siguiente(id,n.siguiente());jdbc.update("update public.crm_ejecucion set reanudar_en=now()+(?*interval '1 minute'),detalle='Espera programada' where id=? and estado='PENDIENTE'",entero(p,"minutos",1,10080),id);log(id,n,"COMPLETADO","Espera de "+p.get("minutos")+" minutos programada");return new Paso(id,contacto,n,false);}
            case "CONDICION"->{boolean si="ESTADO".equals(p.get("campo"))?jdbc.queryForObject("select exists(select 1 from public.crm_contacto where id=? and estado=?)",Boolean.class,contacto,p.get("valor")):jdbc.queryForObject("select exists(select 1 from public.crm_contacto_etiqueta ce join public.crm_etiqueta e on e.id=ce.etiqueta_id where ce.contacto_id=? and lower(e.nombre)=lower(btrim(?)))",Boolean.class,contacto,p.get("valor"));log(id,n,"COMPLETADO",si?"Condición verdadera":"Condición falsa");siguiente(id,si?n.si():n.no());}
            default->{log(id,n,"COMPLETADO","Entrada al segmento");siguiente(id,n.siguiente());}
        }
        return new Paso(id,contacto,n,true);
    }
    private void siguiente(long id,String paso){jdbc.update("update public.crm_ejecucion set paso_id=?,estado=?,reanudar_en=null,detalle=? where id=?",paso, paso==null||paso.isBlank()?"COMPLETADO":"PENDIENTE",paso==null||paso.isBlank()?"Flujo terminado":"Paso pendiente",id);}
    private void log(long id,Nodo n,String estado,String detalle){jdbc.update("insert into public.crm_flujo_paso(ejecucion_id,paso_id,nombre,estado,detalle) values(?,?,?,?,?) on conflict(ejecucion_id,paso_id) do update set estado=excluded.estado,detalle=excluded.detalle,fecha=now()",id,n.id(),n.nombre(),estado,detalle);}
    public List<Map<String,Object>> historial(long flujo,long ejecucion,UUID sujeto){acceso.exigir(sujeto,"automatizaciones",false);return jdbc.queryForList("select p.nombre,p.estado,p.detalle,p.fecha,v.proveedor_id,v.estado estado_proveedor,w.estado entrega,w.fecha fecha_entrega,w.codigo_error from public.crm_flujo_paso p join public.crm_ejecucion e on e.id=p.ejecucion_id left join public.crm_envio v on v.ejecucion_id=p.ejecucion_id and v.paso_id=p.paso_id left join public.crm_whatsapp_entrega w on w.phone_id=v.phone_id and w.proveedor_id=v.proveedor_id where e.automatizacion_id=? and e.id=? order by p.fecha,p.paso_id",flujo,ejecucion);}
    private Map<String,Object>base(long id){var filas=jdbc.queryForList("select * from public.crm_automatizacion where id=?",id);if(filas.isEmpty())throw invalido("Automatización inexistente");return filas.getFirst();}
    private String escribir(Definicion d){try{return json.writeValueAsString(d);}catch(Exception e){throw invalido("Flujo inválido");}}
    private Definicion leer(String s){try{return json.readValue(s,Definicion.class);}catch(Exception e){throw invalido("La definición guardada del flujo no es válida");}}
}
