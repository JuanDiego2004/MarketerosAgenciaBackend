package com.marketing.agencia.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.invalido;

@Service @RequiredArgsConstructor
public class CrmBajasServicio {
    private final JdbcTemplate jdbc;
    @Value("${crm.public-url:}") private String publica;
    public boolean configurada(){try{URI u=URI.create(publica);return "https".equals(u.getScheme())&&u.getHost()!=null&&!u.getHost().equals("localhost")&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null;}catch(Exception e){return false;}}
    @Transactional
    public String enlace(long contacto){
        return enlace(contacto,"EMAIL");
    }
    @Transactional
    public String enlace(long contacto,String canal){
        if(!Set.of("EMAIL","WHATSAPP","SMS").contains(canal))throw invalido("Canal no admitido");
        if(!configurada())throw invalido("Configura CRM_PUBLIC_URL con la dirección HTTPS pública del backend antes de enviar correos");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("insert into public.crm_baja_token(hash,contacto_id,canal) values(?,?,?)",hash(token),contacto,canal);
        return publica.replaceAll("/+$","")+"/api/bajas/"+token;
    }
    @Transactional
    public void revocar(String token){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return;
        var filas=jdbc.queryForList("select contacto_id,canal from public.crm_baja_token where hash=?",hash(token));if(filas.isEmpty())return;
        long id=((Number)filas.getFirst().get("contacto_id")).longValue();
        if(jdbc.queryForList("select id from public.crm_contacto where id=? for update",id).isEmpty())return;
        String canal=filas.getFirst().get("canal").toString();
        if(canal.equals("EMAIL"))jdbc.update("insert into public.crm_baja_email(contacto_id) values(?) on conflict do nothing",id);
        else jdbc.update("insert into public.crm_baja_canal(contacto_id,canal) values(?,?) on conflict do nothing",id,canal);
        jdbc.update("update public.crm_consentimiento set revocado_en=now() where contacto_id=? and upper(tipo)='MARKETING' and upper(canal)=? and revocado_en is null",id,canal);
    }
    static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
