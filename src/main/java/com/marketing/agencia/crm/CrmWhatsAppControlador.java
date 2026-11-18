package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class CrmWhatsAppControlador {
    private final CrmWhatsAppServicio servicio;
    static final int MAX_CUERPO=3*1024*1024;
    public record Vinculo(Long contactoId,int version){}
    @GetMapping(value=CrmWhatsAppServicio.RUTA,produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String>verificar(@RequestParam(name="hub.mode",required=false)String modo,@RequestParam(name="hub.verify_token",required=false)String token,@RequestParam(name="hub.challenge",required=false)String desafio){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.verificar(modo,token,desafio));}
    @PostMapping(value=CrmWhatsAppServicio.RUTA,consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?>recibir(HttpServletRequest r,@RequestHeader(name="X-Hub-Signature-256",required=false)String firma)throws IOException{
        if(r.getContentLengthLong()>MAX_CUERPO)return ResponseEntity.status(413).build();byte[]cuerpo=r.getInputStream().readNBytes(MAX_CUERPO+1);if(cuerpo.length>MAX_CUERPO)return ResponseEntity.status(413).build();
        servicio.recibir(cuerpo,firma);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("EVENT_RECEIVED");
    }
    @GetMapping("/api/crm/operaciones/whatsapp/estado") public Object estado(@AuthenticationPrincipal Jwt j){return ok(servicio.estado(sujeto(j)));}
    @GetMapping("/api/crm/operaciones/whatsapp/mensajes") public Object mensajes(@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="false")boolean pendientes,@AuthenticationPrincipal Jwt j){return ok(servicio.mensajes(null,pagina,pendientes,sujeto(j)));}
    @GetMapping("/api/crm/contactos/{id}/whatsapp") public Object contacto(@PathVariable long id,@RequestParam(defaultValue="0")int pagina,@AuthenticationPrincipal Jwt j){return ok(servicio.mensajes(id,pagina,false,sujeto(j)));}
    @PutMapping("/api/crm/operaciones/whatsapp/mensajes/{id}/contacto") public Object vincular(@PathVariable long id,@RequestBody Vinculo d,@AuthenticationPrincipal Jwt j){servicio.vincular(id,d.contactoId(),d.version(),sujeto(j));return ok(null);}
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)public ResponseEntity<?>datos(){return ResponseEntity.status(503).body(ApiRespuesta.error("No se pudo guardar o consultar el webhook. Comprueba la base y las migraciones hasta crm-v6.sql.","WHATSAPP_BD_NO_DISPONIBLE"));}
    private UUID sujeto(Jwt j){return UUID.fromString(j.getSubject());}
    private Object ok(Object d){return ApiRespuesta.exito("WhatsApp CRM",d);}
}
