package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmPortalPermisosTests {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final CrmAlcanceServicio alcance=mock(CrmAlcanceServicio.class);
    final CrmOperacionServicio auditoria=mock(CrmOperacionServicio.class);
    final UUID usuario=UUID.randomUUID();
    final CrmEntregaServicio servicio=new CrmEntregaServicio(jdbc,mock(CrmAcceso.class),mock(CrmAgenciaServicio.class),alcance,auditoria,mock(CrmInformePdf.class),new ObjectMapper());
    final Map<String,Object> decision=Map.of("estado","APROBADO","version",0,"observacion","");

    void preparar(String tipo,long empresa) {
        when(jdbc.queryForList(contains("crm_portal_acceso"),eq(usuario))).thenReturn(List.of(Map.of("empresa_id",1L)));
        when(jdbc.queryForList(contains("for update of r"),eq(7L))).thenReturn(List.of(Map.of("empresa_id",empresa,"servicio",tipo,"contrato_estado","ACTIVO","contrato_id",10L,"anuncio_id",5L)));
    }
    @Test void soloAnalisisNoApruebaAunqueExistaUnaRevisionAntigua() {
        preparar("ANALISIS",1L);
        assertThrows(ExcepcionApi.class,()->servicio.responder(7L,decision,usuario));
        verifyNoInteractions(alcance,auditoria);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void integralSinPublicacionContratadaNoAprueba() {
        preparar("INTEGRAL",1L);
        doThrow(CrmOperacionServicio.invalido("Prestación no incluida")).when(alcance).exigir(10L,"PUBLICACION");
        assertThrows(ExcepcionApi.class,()->servicio.responder(7L,decision,usuario));
        verifyNoInteractions(auditoria);
    }
    @Test void empresaAjenaNoPuedeResponder() {
        preparar("INTEGRAL",2L);
        assertThrows(ExcepcionApi.class,()->servicio.responder(7L,decision,usuario));
        verifyNoInteractions(alcance,auditoria);
    }
    @Test void integralConPublicacionConservaElFlujoDeAprobacion() {
        preparar("INTEGRAL",1L);
        when(jdbc.queryForObject(contains("id_supabase"),eq(Long.class),eq(usuario))).thenReturn(2L);
        when(jdbc.update(anyString(),eq("APROBADO"),isNull(),eq(2L),eq(7L),eq(0))).thenReturn(1);
        servicio.responder(7L,decision,usuario);
        verify(alcance).exigir(10L,"PUBLICACION");verify(auditoria).auditar("agencia_anuncios",5L,"CLIENTE_APROBADO",2L);
    }
    @Test void analisisNoDescargaMaterialConUnaRevisionAntigua() {
        preparar("ANALISIS",1L);
        when(jdbc.queryForList(contains("select m.*"),eq(9L))).thenReturn(List.of(Map.of("empresa_id",1L,"servicio","ANALISIS","contrato_estado","ACTIVO","contrato_id",10L)));
        assertThrows(ExcepcionApi.class,()->servicio.archivo(9L,usuario,true));
        verifyNoInteractions(alcance);
    }
    @Test void sinPublicacionNoDescargaMaterialAunquePertenezcaALaEmpresa() {
        preparar("INTEGRAL",1L);
        when(jdbc.queryForList(contains("select m.*"),eq(9L))).thenReturn(List.of(Map.of("empresa_id",1L,"servicio","INTEGRAL","contrato_estado","ACTIVO","contrato_id",10L)));
        doThrow(CrmOperacionServicio.invalido("Prestación no incluida")).when(alcance).exigir(10L,"PUBLICACION");
        assertThrows(ExcepcionApi.class,()->servicio.archivo(9L,usuario,true));
    }
}

