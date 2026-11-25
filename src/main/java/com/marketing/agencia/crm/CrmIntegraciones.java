package com.marketing.agencia.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @EnableScheduling @RequiredArgsConstructor
public class CrmIntegraciones {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    private final CrmOperacionServicio operaciones;
    private final TransactionTemplate tx;
    private final CrmBajasServicio bajas;
    private final CrmCorreoServicio correoCrm;
    private final CrmFlujosServicio flujos;
    private final CrmMensajeriaServicio mensajeria;
    @Value("${crm.automatizaciones.habilitadas:false}") private boolean programadas;
    public Map<String,Object> estado(UUID sujeto){acceso.exigir(sujeto,"integraciones",false);return Map.of("publicidadProveedor","Mockaroo","emailConfigurado",correoCrm.configurado(),"automatizacionesProgramadas",programadas,"bajaPublicaConfigurada",bajas.configurada(),"whatsappConfigurado",mensajeria.configurado("WHATSAPP"));}
    public Map<String,Object> destinatariosEmail(UUID sujeto){acceso.exigir(sujeto,"integraciones",false);return Map.of(
        "contactos",jdbc.queryForList("select c.id,c.nombre,c.cuenta_id,cu.nombre empresa from public.crm_contacto c left join public.crm_cuenta cu on cu.id=c.cuenta_id where c.correo is not null and btrim(c.correo)<>'' order by c.nombre limit 1000"),
        "empresas",jdbc.queryForList("select id,nombre from public.crm_cuenta where fusionada_en is null order by nombre limit 1000"),
        "segmentos",jdbc.queryForList("select id,nombre from public.crm_segmento order by nombre limit 1000"));}
    public Map<String,Object> destinatariosWhatsapp(UUID sujeto){acceso.exigir(sujeto,"integraciones",false);return Map.of(
        "contactos",jdbc.queryForList("select c.id,c.nombre,c.cuenta_id,cu.nombre empresa from public.crm_contacto c left join public.crm_cuenta cu on cu.id=c.cuenta_id where c.telefono is not null and btrim(c.telefono)<>'' order by c.nombre limit 1000"),
        "empresas",jdbc.queryForList("select id,nombre from public.crm_cuenta where fusionada_en is null order by nombre limit 1000"),
        "segmentos",jdbc.queryForList("select id,nombre from public.crm_segmento order by nombre limit 1000"));}
    public Map<String,Object> enviarEmail(Map<String,Object> d,UUID sujeto){
        long autor=acceso.exigir(sujeto,"integraciones",true);
        String tipo=textoEmail(d,"tipoDestinatario",20).toUpperCase(Locale.ROOT),asunto=textoEmail(d,"asunto",160),mensaje=textoEmail(d,"mensaje",10000);
        if(!Set.of("CONTACTO","EMPRESA","SEGMENTO","GENERAL").contains(tipo))throw invalido("Elige contacto, empresa, segmento o todos los contactos con consentimiento");
        if(asunto.isBlank()||mensaje.isBlank())throw invalido("Completa el asunto y el mensaje");
        if(!correoCrm.configurado())throw invalido("Configura Brevo, el remitente verificado y la URL pública HTTPS antes de enviar");
        List<Map<String,Object>> candidatos=switch(tipo){
            case "CONTACTO"->{long id=numero(d,"contactoId");yield jdbc.queryForList("select * from public.crm_contacto where id=?",id);}
            case "EMPRESA"->{long id=numero(d,"empresaId");yield jdbc.queryForList("select * from public.crm_contacto where cuenta_id=? and correo is not null and btrim(correo)<>'' order by id limit 10001",id);}
            case "SEGMENTO"->{var ms=operaciones.miembrosInternos(numero(d,"segmentoId"),10001);yield ms;}
            default -> jdbc.queryForList("select c.* from public.crm_contacto c where c.correo is not null and btrim(c.correo)<>'' and exists(select 1 from public.crm_consentimiento co where co.contacto_id=c.id and upper(co.tipo)='MARKETING' and upper(co.canal) in ('EMAIL','CORREO') and co.revocado_en is null and co.fecha<=now()) and not exists(select 1 from public.crm_baja_email b where b.contacto_id=c.id) order by c.id limit 501");
        };
        if(candidatos.size()>10000)throw invalido("El grupo supera 10000 contactos. Acota la empresa o el segmento.");
        if(candidatos.isEmpty())throw invalido("No se encontraron contactos para este destinatario.");
        var elegibles=candidatos.stream().filter(c->correoCrm.consentido(numero(c,"id"))).toList();
        if(elegibles.size()>500)throw invalido("El envío admite hasta 500 contactos con consentimiento. Acota la empresa o el segmento.");
        int enviados=0,omitidos=candidatos.size()-elegibles.size(),errores=0;
        for(var c:elegibles){long contacto=numero(c,"id");try{if(correoCrm.enviar(contacto,asunto,mensaje))enviados++;else omitidos++;}catch(Exception e){errores++;}}
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('integraciones',0,?,?)","ENVIO_EMAIL_"+tipo+"_OK"+enviados+"_OM"+omitidos+"_ER"+errores,autor);
        return Map.of("destinatarios",candidatos.size(),"enviados",enviados,"omitidos",omitidos,"errores",errores);
    }
    public List<Map<String,Object>> historialEmail(UUID sujeto){acceso.exigir(sujeto,"integraciones",false);return jdbc.queryForList("select a.accion,a.created_at,u.nombres responsable from public.crm_auditoria a join public.usuario u on u.id_usuario=a.autor_id where a.recurso='integraciones' and a.accion like 'ENVIO_EMAIL_%' order by a.id desc limit 100");}
    public Map<String,Object> enviarWhatsApp(Map<String,Object> d,UUID sujeto){
        long autor=acceso.exigir(sujeto,"integraciones",true);
        String tipo=textoEmail(d,"tipoDestinatario",20).toUpperCase(Locale.ROOT);
        String plantilla=textoEmail(d,"plantilla",100),idioma=textoEmail(d,"idioma",20);
        Object valorParametros=d.get("parametros");if(valorParametros!=null&&!(valorParametros instanceof String))throw invalido("Revisa los parámetros de la plantilla");
        String parametros=Objects.toString(valorParametros,"");
        if(!Set.of("CONTACTO","EMPRESA","SEGMENTO","GENERAL").contains(tipo))throw invalido("Elige contacto, empresa, segmento o todos los contactos con consentimiento");
        Map<String,Object> p=Map.of("plantilla",plantilla,"idioma",idioma,"parametros",parametros);
        CrmMensajeriaServicio.validar("WHATSAPP",p);
        if(!mensajeria.configurado("WHATSAPP"))throw invalido("Configura WhatsApp Cloud y la URL HTTPS pública antes de enviar");
        List<Map<String,Object>> candidatos=switch(tipo){
            case "CONTACTO"->{long id=numero(d,"contactoId");yield jdbc.queryForList("select * from public.crm_contacto where id=?",id);}
            case "EMPRESA"->{long id=numero(d,"empresaId");yield jdbc.queryForList("select * from public.crm_contacto where cuenta_id=? and telefono is not null and btrim(telefono)<>'' order by id limit 10001",id);}
            case "SEGMENTO"->{var ms=operaciones.miembrosInternos(numero(d,"segmentoId"),10001);yield ms;}
            default -> jdbc.queryForList("select * from public.crm_contacto where telefono is not null and btrim(telefono)<>'' order by id limit 10001");
        };
        if(candidatos.size()>10000)throw invalido("El grupo supera 10000 contactos. Acota la empresa o el segmento.");
        if(candidatos.isEmpty())throw invalido("No se encontraron contactos para este destinatario.");
        var elegibles=candidatos.stream().filter(c->mensajeria.consentido(numero(c,"id"),"WHATSAPP")).toList();
        if(elegibles.size()>100)throw invalido("El envío directo admite hasta 100 contactos con consentimiento. Acota la empresa o el segmento.");
        int enviados=0,omitidos=candidatos.size()-elegibles.size(),errores=0;
        Map<String,Integer> fallos=new LinkedHashMap<>();
        for(var c:elegibles){try{if(mensajeria.enviarDirecto(numero(c,"id"),p)!=null)enviados++;else omitidos++;}catch(Exception e){errores++;String detalle=e instanceof com.marketing.agencia.config.ExcepcionApi api?api.getMessage():"No se pudo confirmar el envío con el proveedor.";if(detalle==null||detalle.isBlank())detalle="No se pudo confirmar el envío con el proveedor.";fallos.merge(detalle,1,Integer::sum);}}
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('integraciones',0,?,?)","ENVIO_WHATSAPP_"+tipo+"_OK"+enviados+"_OM"+omitidos+"_ER"+errores,autor);
        List<Map<String,Object>> detalles=fallos.entrySet().stream().map(e->Map.<String,Object>of("mensaje",e.getKey(),"cantidad",e.getValue())).toList();
        return Map.of("destinatarios",candidatos.size(),"aceptadosMeta",enviados,"omitidos",omitidos,"errores",errores,"detallesError",detalles);
    }
    public List<Map<String,Object>> historialWhatsapp(UUID sujeto){acceso.exigir(sujeto,"integraciones",false);return jdbc.queryForList("select a.accion,a.created_at,u.nombres responsable from public.crm_auditoria a join public.usuario u on u.id_usuario=a.autor_id where a.recurso='integraciones' and a.accion like 'ENVIO_WHATSAPP_%' order by a.id desc limit 100");}
    private String textoEmail(Map<String,Object> d,String campo,int max){Object v=d.get(campo);if(!(v instanceof String s)||s.length()>max)throw invalido("Revisa el campo "+campo);return s.trim();}
    public Map<String,Object> ejecutar(long id,UUID sujeto){acceso.exigir(sujeto,"automatizaciones",true);return ejecutarRegistrado(id);}
    @Scheduled(fixedDelayString="${crm.automatizaciones.intervalo-ms:60000}")
    public void programar(){if(!programadas)return;for(var f:jdbc.queryForList("select a.id from public.crm_automatizacion a join public.usuario u on u.id_usuario=a.autor_id where a.activa and upper(u.estado)='ACTIVO'")){try{ejecutarRegistrado(((Number)f.get("id")).longValue());}catch(Exception ignored){/* Cada destinatario conserva el estado de entrega; no se reenvían resultados inciertos. */}}}
    private Map<String,Object> ejecutarRegistrado(long id){try{var r=ejecutarInterno(id);jdbc.update("update public.crm_automatizacion set ultima_ejecucion=now(),ultimo_error=null where id=?",id);return r;}catch(RuntimeException e){jdbc.update("update public.crm_automatizacion set ultima_ejecucion=now(),ultimo_error=? where id=?","No se pudo ejecutar. Revisa permisos, segmento y configuración de proveedores.",id);throw e;}}
    private Map<String,Object> ejecutarInterno(long id){
        var lista=jdbc.queryForList("select a.*,u.id_supabase from public.crm_automatizacion a join public.usuario u on u.id_usuario=a.autor_id where a.id=? and upper(u.estado)='ACTIVO'",id);
        if(lista.isEmpty())throw invalido("Flujo inexistente o autor inactivo");var a=lista.getFirst();
        acceso.exigir(UUID.fromString(a.get("id_supabase").toString()),"automatizaciones",true);
        if(!Boolean.TRUE.equals(a.get("activa")))throw invalido("Activa el flujo antes de ejecutarlo");
        if(flujos.definida(id))return flujos.ejecutar(a);
        boolean email="EMAIL".equals(a.get("accion"));
        if(email&&!correoCrm.configurado())throw invalido("Configura una clave API de Brevo válida (BREVO_API_KEY, formato xkeysib-...), CRM_SMTP_FROM verificado y CRM_PUBLIC_URL HTTPS antes de enviar");
        var contactos=operaciones.miembrosInternos(numero(a,"segmento_id"),501);
        if(contactos.size()>500)throw invalido("Acota el segmento a 500 contactos por flujo");int procesados=0;
        for(var c:contactos){
            Long ejecucion=tx.execute(s->{
                if(email&&!consentido(numero(c,"id")))return null;
                var nueva=jdbc.queryForList("insert into public.crm_ejecucion(automatizacion_id,contacto_id,estado) values(?,?,'PENDIENTE') on conflict do nothing returning id",id,c.get("id"));
                if(nueva.isEmpty())return null;long e=((Number)nueva.getFirst().get("id")).longValue();
                if(!email){jdbc.update("insert into public.crm_actividad(nombre,tipo,estado,fecha,responsable_id,contacto_id,descripcion) values(?,'SEGUIMIENTO','PENDIENTE',now()+interval '1 day',?,?,?)",a.get("asunto"),c.get("responsable_id"),c.get("id"),a.get("mensaje"));jdbc.update("update public.crm_ejecucion set estado='COMPLETADO' where id=?",e);}return e;
            });
            if(ejecucion==null)continue;procesados++;
            if(email){try{
                if(!consentido(numero(c,"id"))){jdbc.update("update public.crm_ejecucion set estado='OMITIDO',detalle='Consentimiento revocado' where id=?",ejecucion);continue;}
                boolean enviado=correoCrm.enviar(numero(c,"id"),a.get("asunto").toString(),a.get("mensaje").toString());
                jdbc.update("update public.crm_ejecucion set estado=? where id=?",enviado?"ENVIADO":"OMITIDO",ejecucion);
            }catch(Exception e){jdbc.update("update public.crm_ejecucion set estado='ERROR',detalle='Entrega no confirmada; verificar proveedor antes de reenviar' where id=?",ejecucion);}}
        }return Map.of("procesados",procesados,"estadisticas",jdbc.queryForList("select estado,count(*) cantidad from public.crm_ejecucion where automatizacion_id=? group by estado",id));
    }
    boolean consentido(long id){return correoCrm.consentido(id);}
    public List<Map<String,Object>> ejecuciones(long id,UUID sujeto){acceso.exigir(sujeto,"automatizaciones",false);return jdbc.queryForList("select id,contacto_id,estado,detalle,created_at,paso_id,reanudar_en from public.crm_ejecucion where automatizacion_id=? order by id desc limit 100",id);}
}
