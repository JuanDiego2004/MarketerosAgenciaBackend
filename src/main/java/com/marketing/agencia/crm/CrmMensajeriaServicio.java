package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

/** Invocado solo después de reservar el paso del flujo. Sin reintentos de envíos. */
@Service @RequiredArgsConstructor
public class CrmMensajeriaServicio {
    private final JdbcTemplate jdbc;private final CrmProveedorHttp http;private final CrmBajasServicio bajas;
    @Value("${crm.whatsapp.token:}") private String whatsappToken;
    @Value("${crm.whatsapp.phone-id:}") private String phoneId;
    @Value("${crm.whatsapp.version:v27.0}") private String version;
    @Value("${crm.twilio.account-sid:}") private String sid;
    @Value("${crm.twilio.auth-token:}") private String twilioToken;
    @Value("${crm.twilio.from:}") private String desde;
    public boolean configurado(String canal){return bajas.configurada()&&("WHATSAPP".equals(canal)?!whatsappToken.isBlank()&&phoneId.matches("[0-9]{1,30}")&&version.matches("v[0-9]+\\.[0-9]+"):"SMS".equals(canal)&&sid.matches("AC[0-9a-fA-F]{32}")&&!twilioToken.isBlank()&&desde.matches("\\+[1-9][0-9]{7,14}"));}
    public boolean consentido(long contacto,String canal){return jdbc.queryForObject("select exists(select 1 from public.crm_contacto c where c.id=? and c.telefono ~ '^[1-9][0-9]{7,14}$' and exists(select 1 from public.crm_consentimiento s where s.contacto_id=c.id and upper(s.tipo)='MARKETING' and upper(s.canal)=? and s.revocado_en is null) and not exists(select 1 from public.crm_baja_canal b where b.contacto_id=c.id and b.canal=?))",Boolean.class,contacto,canal,canal);}
    public boolean enviar(long ejecucion,long contacto,CrmFlujosServicio.Nodo nodo){
        String canal=nodo.tipo();validar(canal,nodo.parametros());if(!configurado(canal))throw invalido("Configura el proveedor y la URL HTTPS pública antes de enviar "+canal);
        if(!consentido(contacto,canal))return false;
        if(jdbc.update("insert into public.crm_envio(ejecucion_id,paso_id,canal,estado) values(?,?,?,'INCIERTO') on conflict do nothing",ejecucion,nodo.id(),canal)==0)throw invalido("Este envío ya está reservado; revisa su resultado en el proveedor");
        // Revalidar inmediatamente antes de construir el envío; la baja prevalece sobre nuevos consentimientos.
        if(!consentido(contacto,canal)){jdbc.update("update public.crm_envio set estado='OMITIDO' where ejecucion_id=? and paso_id=?",ejecucion,nodo.id());return false;}
        var c=jdbc.queryForMap("select nombre,telefono from public.crm_contacto where id=?",contacto);String enlace=canal.equals("WHATSAPP")&&esPrueba(nodo.parametros())?"":bajas.enlace(contacto,canal);JsonNode r;String proveedor;
        jdbc.update("update public.crm_envio set phone_id=?,destinatario=? where ejecucion_id=? and paso_id=?",canal.equals("WHATSAPP")?phoneId:null,c.get("telefono"),ejecucion,nodo.id());
        if(canal.equals("SMS")){
            String texto=nodo.parametros().get("mensaje").toString().replace("{{nombre}}",c.get("nombre").toString())+"\nBaja: "+enlace;
            if(texto.length()>1600)throw invalido("El SMS final supera 1600 caracteres incluyendo nombre y baja");
            String auth=Base64.getEncoder().encodeToString((sid+":"+twilioToken).getBytes(StandardCharsets.UTF_8));
            r=http.post("https://api.twilio.com/2010-04-01/Accounts/"+sid+"/Messages.json",Map.of("Authorization","Basic "+auth),Map.of("From",desde,"To","+"+c.get("telefono"),"Body",texto),true);proveedor=r.path("sid").asText();
        }else{
            Map<String,Object>plantilla=new HashMap<>(Map.of("name",nodo.parametros().get("plantilla"),"language",Map.of("code",nodo.parametros().get("idioma"))));
            if(!esPrueba(nodo.parametros())){
                List<Map<String,Object>>parametros=new ArrayList<>();for(String p:lineas(nodo.parametros()))parametros.add(Map.of("type","text","text",p.replace("{{nombre}}",c.get("nombre").toString()).replace("{{baja}}",enlace)));
                plantilla.put("components",List.of(Map.of("type","body","parameters",parametros)));
            }
            r=http.post("https://graph.facebook.com/"+version+"/"+phoneId+"/messages",Map.of("Authorization","Bearer "+whatsappToken),Map.of("messaging_product","whatsapp","to",c.get("telefono"),"type","template","template",plantilla),false);proveedor=r.path("messages").path(0).path("id").asText();
        }
        if(proveedor.isBlank()||proveedor.length()>200)throw invalido("El proveedor no confirmó un identificador de envío");
        jdbc.update("update public.crm_envio set estado='ACEPTADO',proveedor_id=? where ejecucion_id=? and paso_id=?",proveedor,ejecucion,nodo.id());return true;
    }
    static void validar(String tipo,Map<String,Object>p){
        if(tipo.equals("SMS")){texto(p,"mensaje",1000,true);return;}
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
