package com.marketing.agencia.crm;
import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@RestController @RequiredArgsConstructor
public class CrmCaptacionControlador {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    @GetMapping("/api/crm/operaciones/formularios")
    public Object listar(@RequestParam long campanaId,@AuthenticationPrincipal Jwt j){acceso.exigir(UUID.fromString(j.getSubject()),"pipeline",false);return ApiRespuesta.exito("Formularios",jdbc.queryForList("select id,nombre,activo,created_at from public.crm_formulario where campana_id=? order by created_at desc limit 100",campanaId));}
    @PostMapping("/api/crm/operaciones/formularios")
    public Object crear(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){long autor=acceso.exigir(UUID.fromString(j.getSubject()),"pipeline",true);long campana=numero(d,"campana_id");if(jdbc.queryForObject("select count(*) from public.campana where id_campana=?",Long.class,campana)==0)throw invalido("Campaña inexistente");UUID id=UUID.randomUUID();String secreto=UUID.randomUUID()+"-"+UUID.randomUUID();jdbc.update("insert into public.crm_formulario(id,campana_id,nombre,responsable_id,secreto_hash) values(?,?,?,?,?)",id,campana,texto(d,"nombre",160,true),autor,hash(secreto));return ApiRespuesta.exito("Guarda el secreto: solo se muestra ahora",Map.of("id",id,"secreto",secreto,"formulario","/api/captacion/"+id,"webhook","/api/captacion/"+id+"/webhook"));}
    @DeleteMapping("/api/crm/operaciones/formularios/{id}")
    public Object desactivar(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){acceso.exigir(UUID.fromString(j.getSubject()),"pipeline",true);jdbc.update("update public.crm_formulario set activo=false where id=?",id);return ApiRespuesta.exito("Formulario desactivado",null);}
    @GetMapping(value="/api/captacion/{id}",produces=MediaType.TEXT_HTML_VALUE)
    public String formulario(@PathVariable UUID id,@RequestParam Map<String,String> parametros){var f=activo(id);String nombre=HtmlUtils.htmlEscape(f.get("nombre").toString());return """
        <!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>%s</title><style>body{font:16px system-ui;color:#0f172a;padding:20px}label{display:block;margin:12px 0}input{display:block;padding:10px;border:1px solid #94a3b8;border-radius:6px;max-width:100%%;box-sizing:border-box}button{background:#1d4ed8;color:white;border:0;border-radius:6px;padding:12px;cursor:pointer}.trampa{display:none}</style>
        <h1>%s</h1><form method="post" action="/api/captacion/%s" accept-charset="UTF-8">
        <input type="hidden" name="idempotencia" value="%s">
        <input type="hidden" name="utm_source" value="%s"><input type="hidden" name="utm_medium" value="%s"><input type="hidden" name="utm_campaign" value="%s">
        <label>Nombre<input name="nombre" maxlength="160" required autocomplete="name"></label>
        <label>Correo<input name="correo" type="email" maxlength="180" required autocomplete="email"></label>
        <label>Teléfono<input name="telefono" maxlength="30" autocomplete="tel"></label>
        <label><input type="checkbox" name="consentimiento" value="true">Acepto recibir comunicaciones de marketing por correo electrónico.</label>
        <label class="trampa" aria-hidden="true">Sitio web<input name="website" tabindex="-1" autocomplete="off"></label>
        <button type="submit">Enviar mis datos</button></form></html>
        """.formatted(nombre,nombre,id,UUID.randomUUID(),utm(parametros,"utm_source"),utm(parametros,"utm_medium"),utm(parametros,"utm_campaign"));}
    @PostMapping(value="/api/captacion/{id}",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.TEXT_HTML_VALUE)
    @Transactional
    public String enviar(@PathVariable UUID id,@RequestParam Map<String,String>d){
        Captura c=new Captura(d.get("nombre"),d.get("correo"),d.get("telefono"),UUID.fromString(d.getOrDefault("idempotencia",UUID.randomUUID().toString())),"true".equals(d.get("consentimiento")),d.get("utm_source"),d.get("utm_medium"),d.get("utm_campaign"),d.get("website"));
        capturar(id,c,null);return "<!doctype html><html lang='es'><meta charset='utf-8'><title>Solicitud recibida</title><p>Gracias. Recibimos tu solicitud.</p></html>";
    }
    public record Captura(@NotBlank @Size(max=160)String nombre,@NotBlank @Email @Size(max=180)String correo,@Size(max=30)String telefono,@NotNull UUID idempotencia,boolean consentimiento,@Size(max=120)String utm_source,@Size(max=120)String utm_medium,@Size(max=120)String utm_campaign,String website){}
    @PostMapping("/api/captacion/{id}/webhook") @Transactional
    public Object webhook(@PathVariable UUID id,@RequestHeader("X-CRM-Key")String clave,@RequestBody @Valid Captura c){capturar(id,c,clave);return ApiRespuesta.exito("Solicitud recibida",null);}
    private void capturar(UUID id,Captura c,String clave){
        var f=activo(id);
        if(java.util.stream.Stream.of(c.utm_source(),c.utm_medium(),c.utm_campaign()).anyMatch(v->v!=null&&v.length()>120))throw invalido("UTM demasiado largo");
        if(clave!=null&&!MessageDigest.isEqual(hash(clave).getBytes(StandardCharsets.UTF_8),f.get("secreto_hash").toString().getBytes(StandardCharsets.UTF_8)))throw prohibido();
        if(!vacio(c.website()))return;
        if(c.nombre()==null||c.nombre().isBlank()||c.nombre().length()>160||c.correo()==null||c.correo().length()>180||!c.correo().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalido("Revisa tu nombre y correo");
        jdbc.queryForList("select pg_advisory_xact_lock(?)",(long)id.hashCode());
        if(jdbc.queryForObject("select count(*) from public.crm_origen where idempotencia=?",Long.class,c.idempotencia())>0)return;
        if(jdbc.queryForObject("select count(*) from public.crm_origen where campana_id=? and created_at>now()-interval '1 hour'",Long.class,f.get("campana_id"))>=100)throw new com.marketing.agencia.config.ExcepcionApi(HttpStatus.TOO_MANY_REQUESTS,"CRM_LIMITE","Inténtalo más tarde");
        String correo=c.correo().trim().toLowerCase(Locale.ROOT),telefono=CrmServicio.normalizarTelefono(c.telefono());
        var creados=jdbc.queryForList("insert into public.crm_contacto(nombre,correo,telefono,estado,origen,responsable_id,campana_id) values(?,?,?,'NUEVO','FORMULARIO',?,?) on conflict do nothing returning id",c.nombre().trim(),correo,telefono,f.get("responsable_id"),f.get("campana_id"));
        // No se modifica ni se revela una ficha existente al recibir datos públicos.
        boolean nuevo=!creados.isEmpty();long contacto;
        if(nuevo)contacto=((Number)creados.getFirst().get("id")).longValue();
        else { if(clave==null)return;var existentes=jdbc.queryForList("select id from public.crm_contacto where lower(correo)=? for update",correo);if(existentes.isEmpty())return;contacto=((Number)existentes.getFirst().get("id")).longValue(); }
        // Solo el webhook autenticado añade interacciones a contactos existentes; no modifica identidad ni consentimiento.
        jdbc.update("insert into public.crm_origen(contacto_id,campana_id,canal,utm_source,utm_medium,utm_campaign,idempotencia) values(?,?,'WEB',?,?,?,?)",contacto,f.get("campana_id"),c.utm_source(),c.utm_medium(),c.utm_campaign(),c.idempotencia());
        jdbc.update("insert into public.crm_evento(contacto_id,tipo,detalle,autor_id) values(?,'CAPTACION','Contacto recibido desde formulario',?)",contacto,f.get("responsable_id"));
        if(nuevo&&c.consentimiento())jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,'MARKETING','EMAIL',now(),?)",contacto,f.get("responsable_id"));
    }
    private Map<String,Object> activo(UUID id){var filas=jdbc.queryForList("select f.* from public.crm_formulario f join public.usuario u on u.id_usuario=f.responsable_id where f.id=? and f.activo and upper(u.estado)='ACTIVO'",id);if(filas.isEmpty())throw new com.marketing.agencia.config.ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_FORMULARIO","Formulario no disponible");return filas.getFirst();}
    private String utm(Map<String,String> p,String k){String s=p.getOrDefault(k,"");return HtmlUtils.htmlEscape(s.substring(0,Math.min(s.length(),120)));}
    private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
