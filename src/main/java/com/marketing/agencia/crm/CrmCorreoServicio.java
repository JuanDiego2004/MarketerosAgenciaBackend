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
    @Value("${crm.email.logo-url:https://marketerosfrontend.onrender.com/email-logo.svg}")private String logoUrl;
    @Value("${crm.brevo.api-key:}")private String brevoApiKey;
    @Value("${crm.brevo.api-url:https://api.brevo.com/v3/smtp/email}")private String brevoApiUrl;
    private boolean brevoConfigurado(){return brevoApiKey!=null&&brevoApiKey.startsWith("xkeysib-")&&!brevoApiUrl.isBlank();}
    public boolean configurado(){return brevoConfigurado()&&!remitente.isBlank()&&bajas.configurada();}
    public boolean consentido(long id){return jdbc.queryForObject("select count(*) from public.crm_contacto c where c.id=? and c.correo is not null and not exists(select 1 from public.crm_baja_email b where b.contacto_id=c.id) and exists(select 1 from public.crm_consentimiento co where co.contacto_id=c.id and upper(co.tipo)='MARKETING' and upper(co.canal) in ('EMAIL','CORREO') and co.revocado_en is null and co.fecha<=now())",Long.class,id)>0;}
    public boolean enviar(long contacto,String asunto,String texto)throws Exception{
        if(!configurado())throw invalido("Configura una clave API de Brevo válida (BREVO_API_KEY, formato xkeysib-...), CRM_SMTP_FROM verificado y CRM_PUBLIC_URL HTTPS antes de enviar");
        if(!consentido(contacto))return false;
        var filas=jdbc.queryForList("select nombre,correo from public.crm_contacto where id=?",contacto);if(filas.isEmpty())return false;var c=filas.getFirst();
        String nombre=Objects.toString(c.get("nombre"),""), enlace=bajas.enlace(contacto), destino=c.get("correo").toString();
        String cuerpo=texto.replace("{{nombre}}",nombre)+"\n\nDejar de recibir correos de marketing: "+enlace;
        String html=plantillaHtml(texto.replace("{{nombre}}",nombre),nombre,enlace);
        enviarBrevo(destino,nombre,asunto,cuerpo,html,enlace);return true;
    }
    private String plantillaHtml(String texto,String nombre,String enlace){
        String contenido=escaparHtml(texto).replace("\r\n","\n").replace("\n","<br>");
        String marca=escaparHtml("Marketeros"),logo=escaparHtml(logoUrl),urlBaja=escaparHtml(enlace);
        return """
            <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Mensaje de %s</title></head>
            <body style="margin:0;padding:24px 12px;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#172033">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"><tr><td align="center">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden">
                  <tr><td style="padding:24px 32px;background:#ffffff;border-bottom:1px solid #e2e8f0"><img src="%s" width="168" alt="%s" style="display:block;width:168px;max-width:100%%;height:auto;border:0"></td></tr>
                  <tr><td style="padding:32px"><p style="margin:0 0 12px;color:#64748b;font-size:14px">Un mensaje para ti</p>
                    <h1 style="margin:0 0 24px;color:#172033;font-size:24px;line-height:1.3">Hola %s 👋</h1>
                    <div style="font-size:16px;line-height:1.75;color:#334155;overflow-wrap:anywhere">%s</div>
                    <p style="margin:28px 0 0;color:#334155;font-size:15px;line-height:1.6">¡Gracias por leernos!<br><strong>El equipo de %s</strong></p>
                  </td></tr>
                  <tr><td style="padding:20px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px;line-height:1.6">Recibiste este correo porque autorizaste comunicaciones de marketing. <a href="%s" style="color:#1d4ed8">Cancelar suscripción</a>.</td></tr>
                </table><p style="margin:16px 0 0;color:#94a3b8;font-size:11px">© %s</p>
              </td></tr></table>
            </body></html>
            """.formatted(marca,logo,marca,escaparHtml(nombre),contenido,marca,urlBaja,marca);
    }
    private String escaparHtml(String valor){return Objects.toString(valor,"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private void enviarBrevo(String destino,String nombre,String asunto,String cuerpo,String html,String enlace)throws Exception{
        var payload=new LinkedHashMap<String,Object>();
        payload.put("sender",Map.of("email",remitente,"name","Marketeros"));payload.put("to",List.of(Map.of("email",destino,"name",nombre)));
        payload.put("subject",asunto);payload.put("textContent",cuerpo);payload.put("htmlContent",html);
        var headers=new LinkedHashMap<String,String>();headers.put("List-Unsubscribe","<"+enlace+">");payload.put("headers",headers);
        http.post(brevoApiUrl,Map.of("api-key",brevoApiKey,"accept","application/json"),payload,false);
    }
}
