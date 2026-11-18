package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

// Ruta neutra para evitar falsos positivos de filtros de anuncios. Se conserva el alias anterior.
@RestController @RequiredArgsConstructor @RequestMapping({"/api/crm/rendimiento","/api/crm/demostracion","/api/crm/publicidad-demo"})
public class CrmPublicidadDemoControlador {
    private final CrmPublicidadDemoServicio servicio;
    public record Anulacion(int version){}
    @PostMapping("/inversiones") public ResponseEntity<?> inversion(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ResponseEntity.status(201).body(ok(servicio.inversion(d,sujeto(j))));}
    @PutMapping("/inversiones/{id}/anular") public Object anular(@PathVariable long id,@RequestBody Anulacion d,@AuthenticationPrincipal Jwt j){servicio.anularInversion(id,d.version(),sujeto(j));return ok(null);}
    @GetMapping("/estado") public Object estado(@AuthenticationPrincipal Jwt j){return ok(servicio.estado(sujeto(j)));}
    @GetMapping public Object listar(@AuthenticationPrincipal Jwt j){return ok(servicio.listar(sujeto(j)));}
    @PostMapping public ResponseEntity<?> crear(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ResponseEntity.status(201).body(ok(servicio.crear(d,sujeto(j))));}
    @PostMapping("/{id}/sincronizar") public Object sincronizar(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@AuthenticationPrincipal Jwt j){return ok(servicio.sincronizar(id,desde,hasta,sujeto(j)));}
    @GetMapping("/{id}/metricas") public Object metricas(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(defaultValue="")String pais,@AuthenticationPrincipal Jwt j){return ok(servicio.metricas(id,desde,hasta,pais,sujeto(j)));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("Esa campaña ya está vinculada a la plataforma seleccionada, o ya no está disponible","PUBLICIDAD_DEMO_CONFLICTO"));}
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> esquema(org.springframework.jdbc.BadSqlGrammarException e){if("42P01".equals(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Completa las migraciones CRM hasta crm-v8.sql para habilitar el rendimiento publicitario","CRM_MIGRACION_PENDIENTE"));throw e;}
    private UUID sujeto(Jwt j){return UUID.fromString(j.getSubject());}
    private Object ok(Object d){return ApiRespuesta.exito("Rendimiento publicitario",d);}
}
