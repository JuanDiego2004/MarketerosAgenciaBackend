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
    @Value("${crm.smtp.one-click:false}") private boolean oneClick;
    public Map<String,Object> estado(UUID sujeto){acceso.exigir(sujeto,"integraciones",false);return Map.of("publicidadProveedor","Mockaroo","smtpConfigurado",correoCrm.configurado(),"automatizacionesProgramadas",programadas,"bajaPublicaConfigurada",bajas.configurada(),"oneClickConfigurado",oneClick,"whatsappConfigurado",mensajeria.configurado("WHATSAPP"),"smsConfigurado",mensajeria.configurado("SMS"));}
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
        if(email&&!correoCrm.configurado())throw invalido("Configura SMTP, CRM_SMTP_FROM y CRM_PUBLIC_URL (HTTPS público) antes de enviar");
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
