package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/crm/contratacion") @RequiredArgsConstructor
public class CrmContratoControlador {
 private final CrmContratoServicio s;
 @PostMapping("/contratos/{id}/ampliaciones") public Object ampliar(@PathVariable long id,@AuthenticationPrincipal Jwt j)throws Exception{return ApiRespuesta.exito("Borrador contextual preparado",s.prepararAmpliacion(id,u(j)));}
 @PostMapping("/contratos/{id}/anuncios/{ad}/mockaroo") public Object mockaroo(@PathVariable long id,@PathVariable long ad,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Datos simulados asociados",s.sincronizar(id,ad,d,u(j)));}
 @PostMapping("/contratos/{id}/contenido/{contenido}") public Object contenido(@PathVariable long id,@PathVariable long contenido,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){s.contenido(id,contenido,d,u(j));return ApiRespuesta.exito("Estado del contenido actualizado",null);}
 private UUID u(Jwt j){return UUID.fromString(j.getSubject());}
 @GetMapping("/borradores") public Object borradores(@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Borradores",s.borradores(u(j)));}
 @GetMapping("/borradores/{id}") public Object alta(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Borrador",s.detalleAlta(id,u(j)));}
 @PutMapping("/borradores/{id}") public Object guardar(@PathVariable UUID id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j)throws Exception{return ApiRespuesta.exito("Borrador guardado",s.guardarAlta(id,d,u(j)));}
 @PostMapping("/borradores/{id}/archivos/{clave}") public Object subir(@PathVariable UUID id,@PathVariable UUID clave,@RequestParam MultipartFile archivo,@AuthenticationPrincipal Jwt j)throws Exception{return ApiRespuesta.exito("Archivo vinculado al borrador",s.subir(id,clave,archivo,u(j)));}
 @PostMapping("/borradores/{id}/confirmar") public Object confirmar(@PathVariable UUID id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j)throws Exception{return ApiRespuesta.exito("Contrato creado",s.confirmar(id,CrmOperacionServicio.entero(d,"version",0,Integer.MAX_VALUE),u(j)));}
 @GetMapping("/referencias") public Object buscar(@RequestParam long empresa,@RequestParam String plataforma,@RequestParam String referencia,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Referencias locales",s.buscar(empresa,plataforma,referencia,u(j)));}
 @GetMapping("/contratos/{id}") public Object detalle(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Ficha del contrato",s.detalle(id,u(j)));}
 @GetMapping("/archivos/{id}") public ResponseEntity<?> archivo(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){var f=s.archivo(id,u(j));return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.get("mime").toString())).cacheControl(CacheControl.noStore()).body(new ByteArrayResource((byte[])f.get("contenido")));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("Revisa las relaciones y cantidades. El registro ya existe o cambió","CRM_CONTRATACION_CONFLICTO"));}
 @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> migracion(org.springframework.jdbc.BadSqlGrammarException e){if(Set.of("42P01","42703").contains(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Aplica crm-v12.sql y crm-v13.sql para habilitar la contratación contextual","CRM_MIGRACION_PENDIENTE"));throw e;}
}
