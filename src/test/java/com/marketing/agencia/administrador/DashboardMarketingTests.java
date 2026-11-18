package com.marketing.agencia.administrador;

import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.marketing.agencia.administrador.DashboardMarketingControlador.Resultado;

class DashboardMarketingTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdministradorServicio acceso = mock(AdministradorServicio.class);
    private final DashboardMarketingServicio servicio = new DashboardMarketingServicio(jdbc, acceso);
    private final UUID usuario = UUID.randomUUID();

    @Test
    void validaPeriodoAntesDeConsultarDatos() {
        assertThrows(ExcepcionApi.class, () -> servicio.obtener(LocalDate.of(2026,11,2),LocalDate.of(2026,11,1),usuario));
        verify(acceso).autorizarDashboard(usuario);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rechazaDesgloseSuperiorAlTotalIncluyendoOtrasCiudades() {
        prepararMetrica();
        Resultado resultado = new Resultado(90,2,1,BigDecimal.TEN,BigDecimal.TEN);
        assertThrows(ExcepcionApi.class, () -> servicio.resultado(42,1,resultado,usuario));
        verify(acceso).autorizarGeografia(usuario,true);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test
    void reemplazaElDesgloseDeLaMismaCiudadSinDuplicarlo() {
        prepararMetrica();
        Resultado resultado = new Resultado(80,2,1,BigDecimal.TEN,BigDecimal.TEN);
        servicio.resultado(42,1,resultado,usuario);
        verify(jdbc).queryForList(contains("for update"),eq(42L));
        verify(jdbc).queryForMap(contains("ubicacion_id<>?"),eq(42L),eq(1L));
        verify(jdbc).update(contains("on conflict(metrica_id,ubicacion_id)"),eq(42L),eq(1L),eq(80L),eq(2L),eq(1L),eq(BigDecimal.TEN),eq(BigDecimal.TEN));
    }

    private void prepararMetrica() {
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(1L))).thenReturn(true);
        when(jdbc.queryForList(anyString(),eq(42L))).thenReturn(List.of(Map.of(
            "impresiones",100,"clics",10,"conversiones",2,"inversion",50,"ingresos",100)));
        when(jdbc.queryForMap(anyString(),eq(42L),eq(1L))).thenReturn(Map.of(
            "impresiones",20,"clics",2,"conversiones",1,"inversion",10,"ingresos",20));
    }
}
