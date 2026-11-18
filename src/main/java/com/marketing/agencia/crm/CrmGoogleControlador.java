package com.marketing.agencia.crm;
import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/crm/operaciones/conectores")
public class CrmGoogleControlador {
    private final CrmGoogleServicio servicio;
    public record Estado(int version,boolean activo){}
    @GetMapping("/estado") public Object estado(@AuthenticationPrincipal Jwt j){return ok(servicio.estado(UUID.fromString(j.getSubject())));}
    @GetMapping public Object listar(@RequestParam(defaultValue="0")int pagina,@AuthenticationPrincipal Jwt j){return ok(servicio.listar(pagina,UUID.fromString(j.getSubject())));}
    @PostMapping public ResponseEntity<?>crear(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ResponseEntity.status(201).body(ok(servicio.crear(d,UUID.fromString(j.getSubject()))));}
    @PutMapping("/{id}") public Object activar(@PathVariable long id,@RequestBody Estado d,@AuthenticationPrincipal Jwt j){servicio.activar(id,d.version(),d.activo(),UUID.fromString(j.getSubject()));return ok(null);}
    @PostMapping("/{id}/sincronizar") public Object sincronizar(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@AuthenticationPrincipal Jwt j){return ok(servicio.sincronizar(id,desde,hasta,UUID.fromString(j.getSubject())));}
    @GetMapping("/{id}/metricas") public Object metricas(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@AuthenticationPrincipal Jwt j){return ok(servicio.metricas(id,desde,hasta,UUID.fromString(j.getSubject())));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?>conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("El vínculo ya existe o la campaña no está disponible","CRM_CONECTOR_CONFLICTO"));}
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?>esquema(org.springframework.jdbc.BadSqlGrammarException e){if("42P01".equals(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Falta aplicar crm-v5.sql","CRM_MIGRACION_PENDIENTE"));throw e;}
    private Object ok(Object d){return ApiRespuesta.exito("Conectores de Google",d);}
}
