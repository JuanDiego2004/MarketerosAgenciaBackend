package com.marketing.agencia.administrador;

import com.marketing.agencia.Auth.dto.response.ApiRespuesta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin", "/api/lider", "/api/analista"})
@RequiredArgsConstructor
public class AdministradorControlador {
    private final AdministradorServicio administradorServicio;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiRespuesta<Map<String,Object>>> dashboard(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Resumen administrativo obtenido", administradorServicio.dashboard(idSupabase(jwt))));
    }

    @GetMapping("/{modulo}")
    public ResponseEntity<ApiRespuesta<List<Map<String,Object>>>> listar(@PathVariable String modulo, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Registros obtenidos", administradorServicio.listar(modulo, idSupabase(jwt))));
    }

    @PostMapping("/{modulo}")
    public ResponseEntity<ApiRespuesta<Map<String,Object>>> crear(@PathVariable String modulo, @RequestBody Map<String,Object> datos, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201).body(ApiRespuesta.exito("Registro creado", administradorServicio.guardar(modulo, null, datos, idSupabase(jwt))));
    }

    @PutMapping("/{modulo}/{id}")
    public ResponseEntity<ApiRespuesta<Map<String,Object>>> actualizar(@PathVariable String modulo, @PathVariable Long id, @RequestBody Map<String,Object> datos, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Registro actualizado", administradorServicio.guardar(modulo, id, datos, idSupabase(jwt))));
    }

    @DeleteMapping("/{modulo}/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String modulo, @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        administradorServicio.eliminar(modulo, id, idSupabase(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/campanas/{idCampana}/audiencias/{idSegmento}")
    public ResponseEntity<ApiRespuesta<Map<String,Object>>> vincularAudiencia(@PathVariable Long idCampana, @PathVariable Long idSegmento, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Audiencia vinculada", administradorServicio.vincularAudiencia(idCampana, idSegmento, idSupabase(jwt))));
    }

    @GetMapping("/campanas/{idCampana}/audiencias")
    public ResponseEntity<ApiRespuesta<List<Map<String,Object>>>> listarAudienciasVinculadas(@PathVariable Long idCampana, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Audiencias de campaña obtenidas", administradorServicio.audienciasDeCampana(idCampana, idSupabase(jwt))));
    }

    @PostMapping("/campanas/{idCampana}/kpis")
    public ResponseEntity<ApiRespuesta<Map<String,Object>>> calcularKpis(@PathVariable Long idCampana, @RequestBody @Valid RangoFechas rango, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("KPIs calculados", administradorServicio.calcularKpi(idCampana, rango.fechaDesde(), rango.fechaHasta(), idSupabase(jwt))));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiRespuesta<List<Map<String,Object>>>> listarKpis(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("KPIs obtenidos", administradorServicio.listarKpis(idSupabase(jwt))));
    }

    @GetMapping("/comparacion")
    public ResponseEntity<ApiRespuesta<List<Map<String,Object>>>> comparar(@RequestParam List<Long> ids, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Comparación de campañas obtenida", administradorServicio.compararCampanas(ids, idSupabase(jwt))));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiRespuesta<List<Map<String,Object>>>> roles(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Roles obtenidos", administradorServicio.roles(idSupabase(jwt))));
    }

    @GetMapping("/permisos")
    public ResponseEntity<ApiRespuesta<List<Map<String,Object>>>> permisos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiRespuesta.exito("Permisos obtenidos", administradorServicio.permisos(idSupabase(jwt))));
    }

    @PutMapping("/roles/{idRol}/permisos")
    public ResponseEntity<ApiRespuesta<Void>> asignarPermisos(@PathVariable Long idRol, @RequestBody @Valid PermisosRequest request, @AuthenticationPrincipal Jwt jwt) {
        administradorServicio.asignarPermisos(idRol, request.idPermisos(), idSupabase(jwt));
        return ResponseEntity.ok(ApiRespuesta.exito("Permisos del rol actualizados", null));
    }

    @GetMapping("/reportes/campanas.csv")
    public ResponseEntity<byte[]> exportarCampanas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                    @AuthenticationPrincipal Jwt jwt) {
        byte[] contenido = administradorServicio.exportarCampanasCsv(desde, hasta, idSupabase(jwt));
        return ResponseEntity.ok().contentType(new MediaType("text", "csv")).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-campanas.csv\"").body(contenido);
    }

    private UUID idSupabase(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    public record RangoFechas(@NotNull LocalDate fechaDesde, @NotNull LocalDate fechaHasta) {}
    public record PermisosRequest(@NotNull List<Long> idPermisos) {}
}
