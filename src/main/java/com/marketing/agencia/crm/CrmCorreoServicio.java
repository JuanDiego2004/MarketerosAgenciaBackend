package com.marketing.agencia.crm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.invalido;

/** Único envío de correo CRM: consentimiento, supresión y baja se aplican a todos los flujos. */
@Service @RequiredArgsConstructor
public class CrmCorreoServicio {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> correo;
    private final CrmBajasServicio bajas;
    @Value("${crm.smtp.from:}")private String remitente;
    @Value("${crm.smtp.one-click:false}")private boolean oneClick;
    @Value("${spring.mail.host:}")private String host;
    public boolean configurado(){return correo.getIfAvailable()!=null&&!host.isBlank()&&!remitente.isBlank()&&bajas.configurada();}
    public boolean oneClick(){return oneClick;}
    public boolean consentido(long id){return jdbc.queryForObject("select count(*) from public.crm_contacto c where c.id=? and c.correo is not null and not exists(select 1 from public.crm_baja_email b where b.contacto_id=c.id) and exists(select 1 from public.crm_consentimiento co where co.contacto_id=c.id and upper(co.tipo)='MARKETING' and upper(co.canal)='EMAIL' and co.revocado_en is null and co.fecha<=now())",Long.class,id)>0;}
    public boolean enviar(long contacto,String asunto,String texto)throws Exception{
        if(!configurado())throw invalido("Configura SMTP, CRM_SMTP_FROM y CRM_PUBLIC_URL antes de enviar");
        if(!consentido(contacto))return false;
        var filas=jdbc.queryForList("select nombre,correo from public.crm_contacto where id=?",contacto);if(filas.isEmpty())return false;var c=filas.getFirst();
        String enlace=bajas.enlace(contacto);var m=correo.getObject().createMimeMessage();var h=new MimeMessageHelper(m,StandardCharsets.UTF_8.name());
        h.setFrom(remitente);h.setTo(c.get("correo").toString());h.setSubject(asunto);h.setText(texto.replace("{{nombre}}",c.get("nombre").toString())+"\n\nDejar de recibir correos de marketing: "+enlace,false);
        m.setHeader("List-Unsubscribe","<"+enlace+">");if(oneClick)m.setHeader("List-Unsubscribe-Post","List-Unsubscribe=One-Click");correo.getObject().send(m);return true;
    }
}
