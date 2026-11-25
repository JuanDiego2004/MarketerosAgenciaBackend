package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

/** Invocado solo después de reservar el paso del flujo. Sin reintentos de envíos. */
@Service @RequiredArgsConstructor
public class CrmMensajeriaServicio {
    private final JdbcTemplate jdbc;private final CrmProveedorHttp http;private final CrmBajasServicio bajas;
    @Value("${crm.whatsapp.token:}") private String whatsappToken;
    @Value("${crm.whatsapp.phone-id:}") private String phoneId;
    @Value("${crm.whatsapp.version:v27.0}") private String version;
    public boolean configurado(String canal){return "WHATSAPP".equals(canal)&&bajas.configurada()&&!whatsappToken.isBlank()&&phoneId.matches("[0-9]{1,30}")&&version.matches("v[0-9]+\\.[0-9]+");}
    public boolean consentido(long contacto,String canal){return jdbc.queryForObject("select exists(select 1 from public.crm_contacto c where c.id=? and c.telefono ~ '^[1-9][0-9]{7,14}$' and exists(select 1 from public.crm_consentimiento s where s.contacto_id=c.id and upper(s.tipo)='MARKETING' and upper(s.canal)=? and s.revocado_en is null and s.fecha<=now()) and not exists(select 1 from public.crm_baja_canal b where b.contacto_id=c.id and b.canal=?))",Boolean.class,contacto,canal,canal);}
    public boolean enviar(long ejecucion,long contacto,CrmFlujosServicio.Nodo nodo)throws Exception{
        String canal=nodo.tipo();validar(canal,nodo.parametros());if(!configurado(canal))throw invalido("Configura el proveedor y la URL HTTPS pública antes de enviar "+canal);
        if(!consentido(contacto,canal))return false;
        if(jdbc.update("insert into public.crm_envio(ejecucion_id,paso_id,canal,estado) values(?,?,?,'INCIERTO') on conflict do nothing",ejecucion,nodo.id(),canal)==0)throw invalido("Este envío ya está reservado; revisa su resultado en el proveedor");
        // Revalidar inmediatamente antes de construir el envío; la baja prevalece sobre nuevos consentimientos.
        if(!consentido(contacto,canal)){jdbc.update("update public.crm_envio set estado='OMITIDO' where ejecucion_id=? and paso_id=?",ejecucion,nodo.id());return false;}
        var c=jdbc.queryForMap("select nombre,telefono from public.crm_contacto where id=?",contacto);
        jdbc.update("update public.crm_envio set phone_id=?,destinatario=? where ejecucion_id=? and paso_id=?",canal.equals("WHATSAPP")?phoneId:null,c.get("telefono"),ejecucion,nodo.id());
        String proveedor=enviarProveedor(contacto,c.get("nombre").toString(),c.get("telefono").toString(),nodo.parametros());
        jdbc.update("update public.crm_envio set estado='ACEPTADO',proveedor_id=? where ejecucion_id=? and paso_id=?",proveedor,ejecucion,nodo.id());return true;
    }
    public String enviarDirecto(long contacto,Map<String,Object> parametros)throws Exception{
        validar("WHATSAPP",parametros);if(!configurado("WHATSAPP"))throw invalido("Configura WhatsApp Cloud y la URL HTTPS pública antes de enviar");
        if(!consentido(contacto,"WHATSAPP"))return null;
        var c=jdbc.queryForList("select nombre,telefono from public.crm_contacto where id=?",contacto);if(c.isEmpty())return null;
        if(!consentido(contacto,"WHATSAPP"))return null;
        return enviarProveedor(contacto,c.getFirst().get("nombre").toString(),c.getFirst().get("telefono").toString(),parametros);
    }
    private String enviarProveedor(long contacto,String nombre,String telefono,Map<String,Object> parametros)throws Exception{
        String enlace=esPrueba(parametros)?"":bajas.enlace(contacto,"WHATSAPP");
        Map<String,Object> plantilla=new HashMap<>(Map.of("name",parametros.get("plantilla"),"language",Map.of("code",parametros.get("idioma"))));
        if(!esPrueba(parametros)){
            List<Map<String,Object>> valores=new ArrayList<>();for(String p:lineas(parametros))valores.add(Map.of("type","text","text",p.replace("{{nombre}}",nombre).replace("{{baja}}",enlace)));
            plantilla.put("components",List.of(Map.of("type","body","parameters",valores)));
        }
        JsonNode r=http.post("https://graph.facebook.com/"+version+"/"+phoneId+"/messages",Map.of("Authorization","Bearer "+whatsappToken),Map.of("messaging_product","whatsapp","to",telefono,"type","template","template",plantilla),false);
        String proveedor=r.path("messages").path(0).path("id").asText();if(proveedor.isBlank()||proveedor.length()>200)throw invalido("Meta no confirmó un identificador de envío");return proveedor;
    }
    static void validar(String tipo,Map<String,Object>p){
        if(!tipo.equals("WHATSAPP"))throw invalido("Canal de mensajería no admitido");
        if(!texto(p,"plantilla",100,true).matches("[a-z0-9_]+")||!texto(p,"idioma",20,true).matches("[a-z]{2,3}(_[A-Z]{2})?"))throw invalido("Indica el nombre exacto y el idioma de la plantilla aprobada por Meta");
        if("hello_world".equals(p.get("plantilla"))){
            if(!esPrueba(p)||!vacio(p.get("parametros")))throw invalido("La plantilla de prueba hello_world usa el idioma en_US y no admite parámetros de cuerpo");
            return;
        }
        String valores=texto(p,"parametros",4000,true);List<String>l=lineas(p);if(l.size()>10||l.stream().anyMatch(s->s.isBlank()||s.length()>500)||!valores.contains("{{baja}}"))throw invalido("La plantilla requiere de 1 a 10 parámetros de cuerpo, uno por línea, incluyendo {{baja}} para cancelar la suscripción");
    }
    static boolean esPrueba(Map<String,Object>p){return "hello_world".equals(p.get("plantilla"))&&"en_US".equals(p.get("idioma"));}
    static List<String>lineas(Map<String,Object>p){return Arrays.asList(p.get("parametros").toString().split("\\r?\\n",-1));}
}
