package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/crm/agencia") @RequiredArgsConstructor
public class CrmAgenciaControlador {
 private final CrmAgenciaServicio servicio;
 @PostMapping("/escenarios") public Object escenarios(@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Escenarios preparados",servicio.escenarios(UUID.fromString(j.getSubject())));}
 @PutMapping("/catalogo/{codigo}") public Object catalogo(@PathVariable String codigo,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){servicio.catalogo(codigo,d,UUID.fromString(j.getSubject()));return ApiRespuesta.exito("Servicio actualizado",null);}
 @GetMapping("/opciones") public Object opciones(@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Servicios de agencia",servicio.opciones(UUID.fromString(j.getSubject())));}
 @GetMapping("/{recurso}") public Object listar(@PathVariable String recurso,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") long contrato,@RequestParam(defaultValue="false") boolean soloDifusion,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Registros",servicio.listar(soloDifusion&&recurso.equals("anuncios")?"calendario":recurso,pagina,q,contrato,UUID.fromString(j.getSubject())));}
 @PostMapping("/{recurso}") public Object crear(@PathVariable String recurso,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Registro creado",servicio.guardar(recurso,null,d,UUID.fromString(j.getSubject())));}
 @PutMapping("/{recurso}/{id}") public Object editar(@PathVariable String recurso,@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Registro actualizado",servicio.guardar(recurso,id,d,UUID.fromString(j.getSubject())));}
 @PostMapping("/{recurso}/{id}/estado") public Object estado(@PathVariable String recurso,@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){servicio.estado(recurso,id,d,UUID.fromString(j.getSubject()));return ApiRespuesta.exito("Estado actualizado",null);}
 @PostMapping("/contratos/{id}/campanas") public Object vincular(@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){servicio.vincular(id,CrmOperacionServicio.numero(d,"campana_id"),UUID.fromString(j.getSubject()));return ApiRespuesta.exito("Campaña vinculada",null);}
 @PostMapping("/anuncios/{id}/sincronizar") public Object sincronizar(@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Estadísticas actualizadas",servicio.sincronizar(id,CrmOperacionServicio.fecha(d,"desde"),CrmOperacionServicio.fecha(d,"hasta"),UUID.fromString(j.getSubject())));}
 @GetMapping("/anuncios/{id}/metricas") public Object metricas(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Datos simulados con Mockaroo",servicio.metricas(id,desde,hasta,UUID.fromString(j.getSubject())));}
 @GetMapping("/contratos/{id}/informe") public Object informe(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Informe del contrato",servicio.informe(id,UUID.fromString(j.getSubject())));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("El código ya existe o una relación no es válida", "CRM_AGENCIA_CONFLICTO"));}
 @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> migracion(org.springframework.jdbc.BadSqlGrammarException e){if(Set.of("42P01","42703").contains(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Aplica crm-v10.sql en la base del backend para habilitar Contratos y servicios","CRM_MIGRACION_PENDIENTE"));throw e;}
}
