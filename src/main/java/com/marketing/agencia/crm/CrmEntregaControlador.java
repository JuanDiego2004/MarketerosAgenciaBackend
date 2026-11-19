package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/crm/entregas") @RequiredArgsConstructor
public class CrmEntregaControlador {
 private final CrmEntregaServicio s;
 private UUID u(Jwt j){return UUID.fromString(j.getSubject());}
 @GetMapping("/anuncios/{id}/medios") public Object medios(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Materiales",s.medios(id,u(j)));}
 @PostMapping("/anuncios/{id}/medios") public Object subir(@PathVariable long id,@RequestParam MultipartFile archivo,@AuthenticationPrincipal Jwt j)throws Exception{return ApiRespuesta.exito("Material guardado",s.subir(id,archivo,u(j)));}
 @GetMapping("/medios/{id}") public ResponseEntity<?> medio(@PathVariable long id,@AuthenticationPrincipal Jwt j){return archivo(id,u(j),false);}
 @GetMapping("/portal/medios/{id}") public ResponseEntity<?> medioPortal(@PathVariable long id,@AuthenticationPrincipal Jwt j){return archivo(id,u(j),true);}
 private ResponseEntity<?> archivo(long id,UUID u,boolean portal){var a=s.archivo(id,u,portal);byte[] bytes=(byte[])a.get("contenido");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(a.get("mime").toString())).contentLength(bytes.length).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(a.get("nombre").toString(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(new ByteArrayResource(bytes));}
 @GetMapping("/comparacion") public Object comparar(@RequestParam long contrato,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam List<Long> anuncios,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Comparación",s.comparar(contrato,desde,hasta,anuncios,u(j)));}
 @GetMapping("/contratos/{id}/versiones") public Object versiones(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Versiones",s.versiones(id,u(j),false));}
 @PostMapping("/contratos/{id}/versiones") public Object generar(@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j)throws Exception{return ApiRespuesta.exito("PDF generado",s.generar(id,d,u(j)));}
 @PostMapping("/versiones/{id}/publicar") public Object publicar(@PathVariable long id,@AuthenticationPrincipal Jwt j){s.publicar(id,u(j));return ApiRespuesta.exito("Informe disponible para el cliente",null);}
 @GetMapping("/versiones/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable long id,@AuthenticationPrincipal Jwt j){return pdf(id,u(j),false);}
 @GetMapping("/portal/versiones/{id}/pdf") public ResponseEntity<byte[]> pdfPortal(@PathVariable long id,@AuthenticationPrincipal Jwt j){return pdf(id,u(j),true);}
 private ResponseEntity<byte[]> pdf(long id,UUID u,boolean portal){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=informe-"+id+".pdf").body(s.descargarPdf(id,u,portal));}
 @GetMapping("/accesos") public Object accesos(@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Accesos al portal",s.accesos(u(j)));}
 @PutMapping("/accesos") public Object asignar(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){s.asignar(d,u(j));return ApiRespuesta.exito("Acceso actualizado",null);}
 @PostMapping("/anuncios/{id}/revision") public Object solicitar(@PathVariable long id,@AuthenticationPrincipal Jwt j){s.solicitarRevision(id,u(j));return ApiRespuesta.exito("Revisión solicitada al cliente",null);}
 @GetMapping("/portal") public Object portal(@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Mi empresa",s.portal(u(j)));}
 @GetMapping("/portal/contratos/{id}") public Object portalContrato(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Mi contrato",s.portalContrato(id,u(j)));}
 @PostMapping("/portal/revisiones/{id}") public Object responder(@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){s.responder(id,d,u(j));return ApiRespuesta.exito("Decisión registrada",null);}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("El registro ya existe o cambió una relación. Actualiza antes de reintentar","CRM_ENTREGA_CONFLICTO"));}
 @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> esquema(org.springframework.jdbc.BadSqlGrammarException e){if(Set.of("42P01","42703").contains(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Aplica crm-v11.sql antes de habilitar entregas y portal","CRM_MIGRACION_PENDIENTE"));throw e;}
}
