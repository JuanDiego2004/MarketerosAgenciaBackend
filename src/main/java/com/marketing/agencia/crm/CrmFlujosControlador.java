package com.marketing.agencia.crm;
import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/crm/operaciones/automatizaciones/{id}/flujo")
public class CrmFlujosControlador {
    private final CrmFlujosServicio servicio;
    @GetMapping public Object obtener(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Flujo",servicio.obtener(id,UUID.fromString(j.getSubject())));}
    @PutMapping public Object guardar(@PathVariable long id,@RequestBody CrmFlujosServicio.Edicion d,@AuthenticationPrincipal Jwt j){servicio.guardar(id,d,UUID.fromString(j.getSubject()));return ApiRespuesta.exito("Flujo guardado",null);}
    @GetMapping("/ejecuciones/{ejecucion}")public Object historial(@PathVariable long id,@PathVariable long ejecucion,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Pasos del flujo",servicio.historial(id,ejecucion,UUID.fromString(j.getSubject())));}
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class)public ResponseEntity<?> esquema(org.springframework.jdbc.BadSqlGrammarException e){if("42P01".equals(e.getSQLException().getSQLState())||"42703".equals(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Aplica las migraciones CRM hasta crm-v5.sql","CRM_MIGRACION_PENDIENTE"));throw e;}
}
