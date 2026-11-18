package com.marketing.agencia.administrador;

import com.marketing.agencia.auth.dto.response.ApiRespuesta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/dashboard/marketing", "/api/lider/dashboard/marketing", "/api/analista/dashboard/marketing"})
@RequiredArgsConstructor
public class DashboardMarketingControlador {
    private final DashboardMarketingServicio servicio;

    @GetMapping
    public Object obtener(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
                          @AuthenticationPrincipal Jwt jwt) {
        return ApiRespuesta.exito("Dashboard de marketing obtenido", servicio.obtener(desde, hasta, sujeto(jwt)));
    }

    @PostMapping("/campanas/{campana}/ubicaciones")
    public Object cobertura(@PathVariable long campana, @RequestBody @Valid Ubicacion ubicacion,
                            @AuthenticationPrincipal Jwt jwt) {
        return ApiRespuesta.exito("Ubicación vinculada", servicio.cobertura(campana, ubicacion, sujeto(jwt)));
    }

    @GetMapping("/campanas/{campana}/geografia")
    public Object detalle(@PathVariable long campana,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
                          @AuthenticationPrincipal Jwt jwt) {
        return ApiRespuesta.exito("Desglose de campaña obtenido", servicio.detalle(campana, desde, hasta, sujeto(jwt)));
    }

    @DeleteMapping("/campanas/{campana}/ubicaciones/{ubicacion}")
    public Object quitarCobertura(@PathVariable long campana, @PathVariable long ubicacion,
                                  @AuthenticationPrincipal Jwt jwt) {
        servicio.quitarCobertura(campana, ubicacion, sujeto(jwt));
        return ApiRespuesta.exito("Cobertura retirada", null);
    }

    @PutMapping("/metricas/{metrica}/ubicaciones/{ubicacion}")
    public Object resultado(@PathVariable long metrica, @PathVariable long ubicacion,
                            @RequestBody @Valid Resultado resultado, @AuthenticationPrincipal Jwt jwt) {
        servicio.resultado(metrica, ubicacion, resultado, sujeto(jwt));
        return ApiRespuesta.exito("Desglose guardado", null);
    }

    @DeleteMapping("/metricas/{metrica}/ubicaciones/{ubicacion}")
    public Object quitarResultado(@PathVariable long metrica, @PathVariable long ubicacion,
                                  @AuthenticationPrincipal Jwt jwt) {
        servicio.quitarResultado(metrica, ubicacion, sujeto(jwt));
        return ApiRespuesta.exito("Desglose retirado", null);
    }

    private UUID sujeto(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record Ubicacion(@NotBlank @Size(max=100) String pais,
                            @NotBlank @Pattern(regexp="[A-Za-z]{2}") String codigoPais,
                            @NotNull @Size(max=150) String ciudad,
                            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
                            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud) {}
    public record Resultado(@Min(0) long impresiones, @Min(0) long clics, @Min(0) long conversiones,
                            @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) BigDecimal inversion,
                            @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) BigDecimal ingresos) {}
}
