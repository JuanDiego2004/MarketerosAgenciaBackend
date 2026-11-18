package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmWhatsAppServicio {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CrmAcceso acceso;
    private final CrmOperacionServicio operaciones;
    @Value("${crm.whatsapp.phone-id:}") private String phoneId;
    @Value("${crm.whatsapp.app-secret:}") private String appSecret;
    @Value("${crm.whatsapp.verify-token:}") private String verifyToken;
    @Value("${crm.public-url:}") private String publica;
    private final CrmBajasServicio bajas;
    static final String RUTA="/api/webhooks/whatsapp";
    record Mensaje(String proveedor_id,String remitente,String telefono,String tipo,String texto,boolean truncado,String fecha){}
    record Entrega(String proveedor_id,String estado,String fecha,String codigo_error){}

    boolean configurado(){return phoneId.matches("[0-9]{1,30}")&&!appSecret.isBlank()&&verifyToken.length()>=32&&verifyToken.length()<=256;}
    private void exigirConfiguracion(){if(!configurado())throw new ExcepcionApi(HttpStatus.SERVICE_UNAVAILABLE,"WHATSAPP_CONFIGURACION","Configura el webhook de WhatsApp en el backend");}
    public String verificar(String modo,String token,String desafio){
        exigirConfiguracion();
        if(!"subscribe".equals(modo)||token==null||token.length()>256||!MessageDigest.isEqual(verifyToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))throw firmaInvalida();
        if(desafio==null||desafio.isBlank()||desafio.length()>512)throw invalido("Desafío de verificación inválido");return desafio;
    }
    void validarFirma(byte[]cuerpo,String firma){
        exigirConfiguracion();
        if(firma==null||!firma.matches("sha256=[a-fA-F0-9]{64}"))throw firmaInvalida();
        if(!MessageDigest.isEqual(hmac(appSecret,cuerpo),HexFormat.of().parseHex(firma.substring(7))))throw firmaInvalida();
    }
    static byte[]hmac(String secreto,byte[]cuerpo){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal(cuerpo);}catch(Exception e){throw new IllegalStateException("No se pudo validar la firma del webhook");}}
    private ExcepcionApi firmaInvalida(){return new ExcepcionApi(HttpStatus.FORBIDDEN,"WHATSAPP_FIRMA","Verificación de webhook rechazada");}

    @Transactional public void recibir(byte[]cuerpo,String firma){
        validarFirma(cuerpo,firma);JsonNode raiz;
        try{raiz=json.readTree(cuerpo);}catch(Exception e){throw invalido("JSON de webhook inválido");}
        if(raiz==null||!raiz.isObject())throw invalido("JSON de webhook inválido");
        if(!"whatsapp_business_account".equals(raiz.path("object").asText()))return;
        List<Mensaje>mensajes=new ArrayList<>();List<Entrega>entregas=new ArrayList<>();
        for(JsonNode entrada:raiz.path("entry"))for(JsonNode cambio:entrada.path("changes")){
            JsonNode v=cambio.path("value");
            if(!"messages".equals(cambio.path("field").asText())||!phoneId.equals(v.path("metadata").path("phone_number_id").asText()))continue;
            for(JsonNode m:v.path("messages"))mensajes.add(recibirMensaje(m));
            for(JsonNode s:v.path("statuses")){Entrega e=recibirEstado(s);if(e!=null)entregas.add(e);}
        }
        // Dos escrituras por lote para responder también a entregas agrupadas de Meta.
        if(!mensajes.isEmpty())jdbc.update("insert into public.crm_whatsapp_mensaje(phone_id,proveedor_id,remitente,telefono,tipo,texto,truncado,contacto_id,fecha) select ?,r.proveedor_id,r.remitente,r.telefono,r.tipo,r.texto,r.truncado,c.id,r.fecha from jsonb_to_recordset(?::jsonb) as r(proveedor_id text,remitente text,telefono text,tipo text,texto text,truncado boolean,fecha timestamptz) left join public.crm_contacto c on c.telefono=r.telefono on conflict(phone_id,proveedor_id) do nothing",phoneId,escribir(mensajes));
        if(!entregas.isEmpty())jdbc.update("insert into public.crm_whatsapp_estado(phone_id,proveedor_id,estado,fecha,codigo_error) select ?,r.proveedor_id,r.estado,r.fecha,r.codigo_error from jsonb_to_recordset(?::jsonb) as r(proveedor_id text,estado text,fecha timestamptz,codigo_error text) on conflict(phone_id,proveedor_id,estado) do nothing",phoneId,escribir(entregas));
    }
    private String escribir(Object d){try{return json.writeValueAsString(d);}catch(Exception e){throw invalido("Evento de WhatsApp inválido");}}
    private Mensaje recibirMensaje(JsonNode m){
        String id=cadena(m,"id",200),tipo=cadena(m,"type",40),desde=cadenaOpcional(m,"from",128),bsuid=cadenaOpcional(m,"from_user_id",128);
        String remitente=bsuid!=null?bsuid:desde;if(remitente==null)throw invalido("Falta el remitente del mensaje");
        String telefono=desde!=null&&desde.matches("[1-9][0-9]{7,14}")?desde:null;
        String texto=switch(tipo){
            case "text"->m.path("text").path("body").asText("");
            case "button"->m.path("button").path("text").asText("");
            case "interactive"->m.path("interactive").path("button_reply").path("title").asText(m.path("interactive").path("list_reply").path("title").asText(""));
            case "image","video","document"->m.path(tipo).path("caption").asText("");
            case "reaction"->m.path("reaction").path("emoji").asText("");
            default->"";
        };
        // Se guardan datos de texto, nunca se descargan/ejecutan archivos o enlaces del remitente.
        return new Mensaje(id,remitente,telefono,tipo,texto.substring(0,Math.min(10000,texto.length())),texto.length()>10000,fecha(m).toString());
    }
    private Entrega recibirEstado(JsonNode s){
        String estado=s.path("status").asText("").toUpperCase(Locale.ROOT);if(!Set.of("SENT","DELIVERED","READ","FAILED").contains(estado))return null;
        String error=s.path("errors").path(0).path("code").asText("");if(!error.matches("[0-9]{1,30}"))error=null;
        return new Entrega(cadena(s,"id",200),estado,fecha(s).toString(),error);
    }
    private static String cadena(JsonNode n,String campo,int limite){String v=cadenaOpcional(n,campo,limite);if(v==null)throw invalido("Falta un identificador del evento de WhatsApp");return v;}
    private static String cadenaOpcional(JsonNode n,String campo,int limite){JsonNode dato=n.path(campo);if(dato.isMissingNode()||dato.isNull())return null;if(!dato.isTextual()||dato.asText().isBlank()||dato.asText().length()>limite)throw invalido("Identificador de evento inválido");return dato.asText();}
    private static OffsetDateTime fecha(JsonNode n){try{long segundos=Long.parseLong(n.path("timestamp").asText());if(segundos<0||segundos>253402300799L)throw new IllegalArgumentException();return Instant.ofEpochSecond(segundos).atOffset(ZoneOffset.UTC);}catch(Exception e){throw invalido("Fecha de evento inválida");}}

    public Map<String,Object>estado(UUID u){
        acceso.exigir(u,"integraciones",false);var d=new LinkedHashMap<String,Object>();d.put("configurado",configurado());d.put("url",bajas.configurada()?publica.replaceAll("/+$","")+RUTA:"");
        d.put("ultimoEvento",jdbc.queryForObject("select max(fecha) from (select recibido_en fecha from public.crm_whatsapp_mensaje union all select recibido_en from public.crm_whatsapp_estado) x",OffsetDateTime.class));
        d.put("entregas",jdbc.queryForList("select coalesce(w.estado,'SIN_CONFIRMACION') estado,count(*) cantidad from public.crm_envio e left join public.crm_whatsapp_entrega w on w.phone_id=e.phone_id and w.proveedor_id=e.proveedor_id where e.canal='WHATSAPP' and e.fecha>=now()-interval '30 days' group by coalesce(w.estado,'SIN_CONFIRMACION')"));return d;
    }
    public Map<String,Object>mensajes(Long contacto,int pagina,boolean pendientes,UUID u){
        acceso.exigir(u,contacto==null?"integraciones":"contactos",false);if(pagina<0||pagina>10000)throw invalido("Página inválida");
        if(contacto!=null&&jdbc.queryForObject("select count(*) from public.crm_contacto where id=?",Long.class,contacto)==0)throw new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_CONTACTO","Contacto inexistente");
        String filtro=contacto!=null?" where m.contacto_id=?":pendientes?" where m.contacto_id is null":"";Object[]p=contacto==null?new Object[]{}:new Object[]{contacto};List<Object>args=new ArrayList<>(Arrays.asList(p));args.add(pagina*20);
        return Map.of("filas",jdbc.queryForList("select m.id,m.remitente,m.telefono,m.tipo,m.texto,m.truncado,m.fecha,m.recibido_en,m.contacto_id,m.version,c.nombre contacto from public.crm_whatsapp_mensaje m left join public.crm_contacto c on c.id=m.contacto_id"+filtro+" order by m.fecha desc,m.id desc limit 20 offset ?",args.toArray()),"total",jdbc.queryForObject("select count(*) from public.crm_whatsapp_mensaje m"+filtro,Long.class,p));
    }
    @Transactional public void vincular(long id,Long contacto,int revision,UUID u){
        long autor=acceso.exigir(u,"integraciones",true);acceso.exigir(u,"contactos",true);
        if(contacto!=null&&jdbc.queryForList("select id from public.crm_contacto where id=? for key share",contacto).isEmpty())throw invalido("Selecciona un contacto existente");
        if(jdbc.update("update public.crm_whatsapp_mensaje set contacto_id=?,version=version+1 where id=? and version=?",contacto,id,revision)==0)throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","El mensaje cambió. Recarga antes de vincularlo.");
        operaciones.auditar("whatsapp",id,contacto==null?"DESVINCULAR":"VINCULAR_CONTACTO",autor);
    }
}
