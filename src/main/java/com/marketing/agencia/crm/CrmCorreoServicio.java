package com.marketing.agencia.crm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.invalido;

/** Único envío de correo CRM: consentimiento, supresión y baja se aplican a todos los flujos. */
@Service @RequiredArgsConstructor
public class CrmCorreoServicio {
    private final JdbcTemplate jdbc;
    private final CrmBajasServicio bajas;
    private final CrmProveedorHttp http;
    @Value("${crm.email.from:}")private String remitente;
    @Value("${crm.brevo.api-key:}")private String brevoApiKey;
    @Value("${crm.brevo.api-url:https://api.brevo.com/v3/smtp/email}")private String brevoApiUrl;
    private boolean brevoConfigurado(){return brevoApiKey!=null&&brevoApiKey.startsWith("xkeysib-")&&!brevoApiUrl.isBlank();}
    public boolean configurado(){return brevoConfigurado()&&!remitente.isBlank()&&bajas.configurada();}
    public boolean consentido(long id){return jdbc.queryForObject("select count(*) from public.crm_contacto c where c.id=? and c.correo is not null and not exists(select 1 from public.crm_baja_email b where b.contacto_id=c.id) and exists(select 1 from public.crm_consentimiento co where co.contacto_id=c.id and upper(co.tipo)='MARKETING' and upper(co.canal)='EMAIL' and co.revocado_en is null and co.fecha<=now())",Long.class,id)>0;}
    public boolean enviar(long contacto,String asunto,String texto)throws Exception{
        if(!configurado())throw invalido("Configura una clave API de Brevo válida (BREVO_API_KEY, formato xkeysib-...), CRM_SMTP_FROM verificado y CRM_PUBLIC_URL HTTPS antes de enviar");
        if(!consentido(contacto))return false;
        var filas=jdbc.queryForList("select nombre,correo from public.crm_contacto where id=?",contacto);if(filas.isEmpty())return false;var c=filas.getFirst();
        String enlace=bajas.enlace(contacto), cuerpo=texto.replace("{{nombre}}",c.get("nombre").toString())+"\n\nDejar de recibir correos de marketing: "+enlace, destino=c.get("correo").toString();
        enviarBrevo(destino,asunto,cuerpo,enlace);return true;
    }
    private void enviarBrevo(String destino,String asunto,String cuerpo,String enlace)throws Exception{
        var payload=new LinkedHashMap<String,Object>();
        payload.put("sender",Map.of("email",remitente));payload.put("to",List.of(Map.of("email",destino)));
        payload.put("subject",asunto);payload.put("textContent",cuerpo);
        var headers=new LinkedHashMap<String,String>();headers.put("List-Unsubscribe","<"+enlace+">");payload.put("headers",headers);
        http.post(brevoApiUrl,Map.of("api-key",brevoApiKey,"accept","application/json"),payload,false);
    }
}
