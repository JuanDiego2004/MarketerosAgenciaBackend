package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmAccesoTests {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UUID sujeto=UUID.randomUUID();
    private final CrmAcceso acceso=new CrmAcceso(jdbc);
    private void rol(String nombre) {
        when(jdbc.queryForList(anyString(),eq(sujeto))).thenReturn(List.of(Map.of("id_usuario",7L,"nombre_rol",nombre)));
    }
    @BeforeEach void permisos() { when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(7L),anyString())).thenReturn(1); }
    @Test void analistaConsultaContactos() { rol("ANALISTA"); assertEquals(7,acceso.exigir(sujeto,"contactos",false)); }
    @Test void analistaNoEscribeAunqueTengaPermiso() { rol("ANALISTA"); assertThrows(ExcepcionApi.class,()->acceso.exigir(sujeto,"contactos",true)); }
    @Test void analistaNoConsultaCuentas() { rol("ANALISTA"); assertThrows(ExcepcionApi.class,()->acceso.exigir(sujeto,"cuentas",false)); }
    @Test void liderConPermisoGestiona() { rol("LÍDER"); assertEquals(7,acceso.exigir(sujeto,"cuentas",true)); }
    @Test void liderSinPermisoNoGestiona() { rol("PROJECT MANAGER"); when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(7L),anyString())).thenReturn(0); assertThrows(ExcepcionApi.class,()->acceso.exigir(sujeto,"contactos",true)); }
    @Test void administradorGestiona() { rol("ADMINISTRADOR"); assertEquals(7,acceso.exigir(sujeto,"cuentas",true)); }
    @Test void usuarioInactivoNoEntra() { when(jdbc.queryForList(anyString(),eq(sujeto))).thenReturn(List.of()); assertThrows(ExcepcionApi.class,()->acceso.exigir(sujeto,"contactos",false)); }
    @Test void recursoNoPermitidoNoLlegaASql() { assertThrows(ExcepcionApi.class,()->acceso.exigir(sujeto,"usuario;delete",true)); verify(jdbc,never()).queryForList(anyString(),eq(sujeto)); }
    @Test void normalizaTelefonoParaDeduplicar() { assertEquals("51999888777",CrmServicio.normalizarTelefono("+51 (999) 888-777")); assertEquals("51999888777",CrmServicio.normalizarTelefono("51999888777")); assertNull(CrmServicio.normalizarTelefono(" ")); assertThrows(ExcepcionApi.class,()->CrmServicio.normalizarTelefono("123")); }
    @Test void exportacionEvitaFormulas() { assertEquals("\"'=HYPERLINK(1)\"",CrmServicio.csvCelda("=HYPERLINK(1)")); assertEquals("\"A\"\"B\"",CrmServicio.csvCelda("A\"B")); }
    @Test void exportacionEvitaFormulasConSaltos(){assertEquals("\"'=SUM(1)\ntexto\"",CrmServicio.csvCelda("=SUM(1)\ntexto"));assertEquals("\"'  @SUM(1)\r\ntexto\"",CrmServicio.csvCelda("  @SUM(1)\r\ntexto"));}
}
