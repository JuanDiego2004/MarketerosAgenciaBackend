package com.marketing.agencia.crm;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import com.marketing.agencia.config.ExcepcionApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
public class CrmControlador {
    private final CrmServicio servicio;

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> conflictoDatos() {
        return ResponseEntity.status(409).body(ApiRespuesta.error("Revisa los datos y referencias: el registro está duplicado o tiene una relación inválida", "CRM_CONFLICTO"));
    }
    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class)
    public ResponseEntity<?> esquemaNoPreparado(org.springframework.jdbc.BadSqlGrammarException error) {
        if ("42P01".equals(error.getSQLException().getSQLState()))
            return ResponseEntity.status(503).body(ApiRespuesta.error("El CRM aún no está habilitado en la base de datos. Ejecuta la migración crm-v1.sql del backend", "CRM_MIGRACION_PENDIENTE"));
        throw error;
    }
    public record Registro(@NotBlank @Size(max=160) String nombre, @Email @Size(max=180) String correo,
        @Size(max=30) String telefono, String estado, @Size(max=120) String origen,
        @Size(max=500) String etiquetas, Long cuentaId, Long campanaId, Long responsableId,
        @Size(max=120) String sector, @Size(max=300) String sitioWeb, Long clienteId) {}
    public record Nota(@NotBlank @Size(max=4000) String texto) {}
    public record Consentimiento(@NotBlank @Size(max=60) String tipo, @NotBlank @Size(max=60) String canal, @NotNull OffsetDateTime fecha) {}
    public record Importacion(@NotEmpty @Size(max=500) List<@Valid Registro> filas) {}

    @GetMapping("/capacidades")
    public Object capacidades(@AuthenticationPrincipal Jwt jwt) { return ApiRespuesta.exito("Acceso CRM", servicio.capacidades(sujeto(jwt))); }
    @GetMapping("/{recurso}/opciones")
    public Object opciones(@PathVariable String recurso, @AuthenticationPrincipal Jwt jwt) { return ApiRespuesta.exito("Opciones",servicio.opciones(recurso,sujeto(jwt))); }
    @GetMapping("/{recurso}")
    public Object listar(@PathVariable String recurso, @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="") String estado, @RequestParam(defaultValue="0") int pagina,
        @AuthenticationPrincipal Jwt jwt) { return ApiRespuesta.exito("Registros CRM", servicio.listar(recurso,q,estado,pagina,sujeto(jwt))); }
    @GetMapping("/{recurso}/{id}")
    public Object detalle(@PathVariable String recurso,@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return ApiRespuesta.exito("Ficha CRM", servicio.detalle(recurso,id,sujeto(jwt))); }
    @PostMapping("/{recurso}")
    public ResponseEntity<?> crear(@PathVariable String recurso,@RequestBody @Valid Registro datos,@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.status(201).body(ApiRespuesta.exito("Registro creado",servicio.guardar(recurso,null,datos,sujeto(jwt)))); }
    @PutMapping("/{recurso}/{id}")
    public Object editar(@PathVariable String recurso,@PathVariable long id,@RequestBody @Valid Registro datos,@AuthenticationPrincipal Jwt jwt) { return ApiRespuesta.exito("Registro actualizado",servicio.guardar(recurso,id,datos,sujeto(jwt))); }
    @DeleteMapping("/{recurso}/{id}")
    public ResponseEntity<?> eliminar(@PathVariable String recurso,@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { servicio.eliminar(recurso,id,sujeto(jwt)); return ResponseEntity.noContent().build(); }
    @PostMapping("/{recurso}/{id}/notas")
    public Object nota(@PathVariable String recurso,@PathVariable long id,@RequestBody @Valid Nota datos,@AuthenticationPrincipal Jwt jwt) { servicio.nota(recurso,id,datos.texto(),sujeto(jwt)); return ApiRespuesta.exito("Nota registrada",null); }
    @PostMapping("/contactos/{id}/consentimientos")
    public Object consentimiento(@PathVariable long id,@RequestBody @Valid Consentimiento datos,@AuthenticationPrincipal Jwt jwt) { servicio.consentimiento(id,datos,sujeto(jwt)); return ApiRespuesta.exito("Consentimiento registrado",null); }
    @DeleteMapping("/contactos/{id}/consentimientos/{consentimientoId}")
    public Object revocar(@PathVariable long id,@PathVariable long consentimientoId,@AuthenticationPrincipal Jwt jwt) { servicio.revocar(id,consentimientoId,sujeto(jwt)); return ApiRespuesta.exito("Consentimiento revocado",null); }
    @PostMapping("/contactos/importacion")
    public Object importar(@RequestBody @Valid Importacion datos,@AuthenticationPrincipal Jwt jwt) { return ApiRespuesta.exito("Importación terminada",servicio.importar(datos.filas(),sujeto(jwt))); }
    @GetMapping("/contactos/exportacion")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String estado,@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.ok().contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=contactos.csv").body(servicio.exportar(q,estado,sujeto(jwt))); }
    @PostMapping("/{recurso}/{id}/archivos")
    public Object subir(@PathVariable String recurso,@PathVariable long id,@RequestParam MultipartFile archivo,@AuthenticationPrincipal Jwt jwt) throws java.io.IOException {
        if (archivo.isEmpty() || archivo.getSize()>5242880) throw new ExcepcionApi(HttpStatus.BAD_REQUEST,"ARCHIVO_INVALIDO","El archivo debe pesar entre 1 byte y 5 MB");
        servicio.archivo(recurso,id,archivo.getOriginalFilename(),archivo.getBytes(),sujeto(jwt)); return ApiRespuesta.exito("Archivo adjuntado",null);
    }
    @GetMapping("/{recurso}/{id}/archivos/{archivoId}")
    public ResponseEntity<byte[]> descargar(@PathVariable String recurso,@PathVariable long id,@PathVariable long archivoId,@AuthenticationPrincipal Jwt jwt) {
        var archivo=servicio.descargar(recurso,id,archivoId,sujeto(jwt));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(archivo.get("nombre").toString(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body((byte[])archivo.get("contenido"));
    }
    private UUID sujeto(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
