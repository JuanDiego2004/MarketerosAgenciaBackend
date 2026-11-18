package com.marketing.agencia.crm;
import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/crm/campos")
public class CrmCamposControlador {
    private final CrmCamposServicio servicio;
    public record Valores(long version,Map<String,Object>valores){}
    @GetMapping public Object catalogo(@RequestParam(defaultValue="0")int pagina,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Catálogo de campos",servicio.catalogo(pagina,sujeto(j)));}
    @PostMapping public ResponseEntity<?> crear(@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ResponseEntity.status(201).body(ApiRespuesta.exito("Campo creado",servicio.guardar(null,d,sujeto(j))));}
    @PutMapping("/{id}") public Object editar(@PathVariable long id,@RequestBody Map<String,Object>d,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Campo actualizado",servicio.guardar(id,d,sujeto(j)));}
    @GetMapping("/contactos/{id}") public Object valores(@PathVariable long id,@AuthenticationPrincipal Jwt j){return ApiRespuesta.exito("Campos del contacto",servicio.valores(id,sujeto(j)));}
    @PutMapping("/contactos/{id}") public Object guardar(@PathVariable long id,@RequestBody Valores d,@AuthenticationPrincipal Jwt j){servicio.guardarValores(id,d.version(),d.valores(),sujeto(j));return ApiRespuesta.exito("Campos guardados",null);}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflicto(){return ResponseEntity.status(409).body(ApiRespuesta.error("La clave del campo ya existe o los datos no son válidos","CRM_CAMPO_CONFLICTO"));}
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class) public ResponseEntity<?> esquema(org.springframework.jdbc.BadSqlGrammarException e){if("42P01".equals(e.getSQLException().getSQLState()))return ResponseEntity.status(503).body(ApiRespuesta.error("Falta aplicar crm-v4.sql","CRM_MIGRACION_PENDIENTE"));throw e;}
    private UUID sujeto(Jwt j){return UUID.fromString(j.getSubject());}
}
