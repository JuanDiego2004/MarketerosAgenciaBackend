package com.marketing.agencia.crm;
import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/crm/operaciones") @RequiredArgsConstructor
public class CrmOperacionControlador {
    private final CrmOperacionServicio servicio;
    private final CrmIntegraciones integraciones;
    private final CrmEvolucionServicio evolucion;
    @GetMapping("/capacidades") public Object capacidades(@AuthenticationPrincipal Jwt j){return ok(servicio.capacidades(sujeto(j)));}
    @GetMapping("/{recurso}/opciones") public Object opciones(@PathVariable String recurso,@AuthenticationPrincipal Jwt j){return ok(servicio.opciones(sujeto(j),recurso));}
    @GetMapping("/notificaciones") public Object notificaciones(@AuthenticationPrincipal Jwt j){return ok(servicio.notificaciones(sujeto(j)));}
    @GetMapping("/metas/alertas") public Object alertas(@AuthenticationPrincipal Jwt j){return ok(servicio.alertas(sujeto(j)));}
    @GetMapping("/score/{id}") public Object score(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ok(servicio.score(id,sujeto(j)));}
    @GetMapping("/atribucion") public Object atribucion(@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(defaultValue="PRIMERO")String modelo,@AuthenticationPrincipal Jwt j){return ok(servicio.atribucion(desde,hasta,modelo,sujeto(j)));}
    @GetMapping("/etiquetas") public Object etiquetas(@AuthenticationPrincipal Jwt j){return ok(evolucion.etiquetas(sujeto(j)));}
    @PostMapping("/etiquetas") public Object etiqueta(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ok(evolucion.etiqueta(d,sujeto(j)));}
    @GetMapping("/contactos/{id}/relaciones") public Object relaciones(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ok(evolucion.relaciones(id,sujeto(j)));}
    @PutMapping("/contactos/{id}/etiquetas") public Object asignarEtiquetas(@PathVariable long id,@RequestBody List<Long>ids,@AuthenticationPrincipal Jwt j){evolucion.asignarEtiquetas(id,ids,sujeto(j));return ok(null);}
    @PostMapping("/contactos/{id}/origenes") public Object origen(@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){evolucion.origen(id,d,sujeto(j));return ok(null);}
    @GetMapping("/{recurso}") public Object listar(@PathVariable String recurso,@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="false")boolean propias,@AuthenticationPrincipal Jwt j){return ok(servicio.listar(recurso,pagina,q,propias,sujeto(j)));}
    @GetMapping("/actividades/calendario") public Object calendario(@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(defaultValue="false")boolean propias,@AuthenticationPrincipal Jwt j){return ok(servicio.calendario(desde,hasta,propias,sujeto(j)));}
    @PostMapping("/{recurso}") public ResponseEntity<?> crear(@PathVariable String recurso,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ResponseEntity.status(201).body(ok(servicio.guardar(recurso,null,d,sujeto(j))));}
    @PutMapping("/{recurso}/{id}") public Object editar(@PathVariable String recurso,@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ok(servicio.guardar(recurso,id,d,sujeto(j)));}
    @DeleteMapping("/{recurso}/{id}") public ResponseEntity<?> eliminar(@PathVariable String recurso,@PathVariable long id,@AuthenticationPrincipal Jwt j){servicio.eliminar(recurso,id,sujeto(j));return ResponseEntity.noContent().build();}
    @GetMapping("/segmentos/{id}/miembros") public Object miembros(@PathVariable long id,@RequestParam(defaultValue="0")int pagina,@AuthenticationPrincipal Jwt j){return ok(servicio.segmento(id,pagina,sujeto(j)));}
    @GetMapping("/segmentos/{id}/csv") public ResponseEntity<byte[]> csv(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ResponseEntity.ok().contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=segmento.csv").body(servicio.exportarSegmento(id,sujeto(j)));}
    @GetMapping("/integraciones/estado") public Object estado(@AuthenticationPrincipal Jwt j){return ok(integraciones.estado(sujeto(j)));}
    @GetMapping("/integraciones/destinatarios-email") public Object destinatariosEmail(@AuthenticationPrincipal Jwt j){return ok(integraciones.destinatariosEmail(sujeto(j)));}
    @GetMapping("/integraciones/email/historial") public Object historialEmail(@AuthenticationPrincipal Jwt j){return ok(integraciones.historialEmail(sujeto(j)));}
    @PostMapping("/integraciones/email/enviar") public Object enviarEmail(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ok(integraciones.enviarEmail(d,sujeto(j)));}
    @PostMapping("/automatizaciones/{id}/ejecutar") public Object ejecutar(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ok(integraciones.ejecutar(id,sujeto(j)));}
    @GetMapping("/automatizaciones/{id}/ejecuciones") public Object ejecuciones(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ok(integraciones.ejecuciones(id,sujeto(j)));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("Registro duplicado, relacionado con otros registros o con datos inválidos","CRM_CONFLICTO"));}
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> esquema(org.springframework.jdbc.BadSqlGrammarException e){if("42P01".equals(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Ejecuta crm-v1.sql y después crm-v2.sql para habilitar estos módulos","CRM_MIGRACION_PENDIENTE"));throw e;}
    private Object ok(Object datos){return ApiRespuesta.exito("Operación CRM",datos);}
    private UUID sujeto(Jwt j){return UUID.fromString(j.getSubject());}
}
