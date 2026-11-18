package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/crm/publicidad-demo")
public class CrmPublicidadDemoControlador {
    private final CrmPublicidadDemoServicio servicio;
    @GetMapping("/estado") public Object estado(@AuthenticationPrincipal Jwt j){return ok(servicio.estado(sujeto(j)));}
    @GetMapping public Object listar(@AuthenticationPrincipal Jwt j){return ok(servicio.listar(sujeto(j)));}
    @PostMapping public ResponseEntity<?> crear(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ResponseEntity.status(201).body(ok(servicio.crear(d,sujeto(j))));}
    @PostMapping("/{id}/sincronizar") public Object sincronizar(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@AuthenticationPrincipal Jwt j){return ok(servicio.sincronizar(id,desde,hasta,sujeto(j)));}
    @GetMapping("/{id}/metricas") public Object metricas(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(defaultValue="")String pais,@AuthenticationPrincipal Jwt j){return ok(servicio.metricas(id,desde,hasta,pais,sujeto(j)));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("Esa campaña ya tiene una demostración de la plataforma seleccionada, o ya no está disponible","PUBLICIDAD_DEMO_CONFLICTO"));}
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> esquema(org.springframework.jdbc.BadSqlGrammarException e){if("42P01".equals(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Aplica crm-v7.sql para habilitar la publicidad de demostración","CRM_MIGRACION_PENDIENTE"));throw e;}
    private UUID sujeto(Jwt j){return UUID.fromString(j.getSubject());}
    private Object ok(Object d){return ApiRespuesta.exito("Publicidad de demostración",d);}
}
