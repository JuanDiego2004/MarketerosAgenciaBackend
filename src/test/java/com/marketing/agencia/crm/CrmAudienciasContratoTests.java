package com.marketing.agencia.crm;

import com.marketing.agencia.administrador.AdministradorServicio;
import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrmAudienciasContratoTests {
 final AdministradorServicio administrador=mock(AdministradorServicio.class);
 final UUID usuario=UUID.randomUUID();
 final CrmContratoServicio servicio=new CrmContratoServicio(mock(JdbcTemplate.class),mock(CrmAcceso.class),new ObjectMapper(),mock(CrmAgenciaServicio.class),mock(CrmAlcanceServicio.class),mock(CrmOperacionServicio.class),mock(CrmMockarooServicio.class),administrador);
 Map<String,Object> datos(Map<String,Object> canal){return new LinkedHashMap<>(Map.of("piezas",List.of(Map.of("canales",List.of(canal)))));}
 Map<?,?> canal(Map<String,Object> d){return (Map<?,?>)((List<?>)((Map<?,?>)((List<?>)d.get("piezas")).getFirst()).get("canales")).getFirst();}
 Map<String,Object> audiencia(String estado){return Map.of("idSegmento",4L,"nombre","Jóvenes de Lima","estado",estado,"ubicacionGeografica","Lima, Perú");}
 @Test void resuelveIdConDatosDelCatalogoYConservaLaCoberturaPersonalizada(){
  when(administrador.listar("audiencias",usuario)).thenReturn(List.of(audiencia("ACTIVO")));
  var d=datos(Map.of("audiencia_id","4","audiencia","Nombre manipulado","modalidad","PAGADA","cobertura",""));
  servicio.resolverAudiencias(d,usuario);
  assertEquals("Jóvenes de Lima",canal(d).get("audiencia"));assertEquals("Lima, Perú",canal(d).get("cobertura"));assertEquals("4",canal(d).get("audiencia_id"));
  d=datos(Map.of("audiencia_id",4,"modalidad","PAGADA","cobertura","Miraflores"));servicio.resolverAudiencias(d,usuario);assertEquals("Miraflores",canal(d).get("cobertura"));
 }
 @Test void rechazaAudienciaArchivadaOInexistente(){
  when(administrador.listar("audiencias",usuario)).thenReturn(List.of(audiencia("INACTIVO")));
  assertThrows(ExcepcionApi.class,()->servicio.resolverAudiencias(datos(Map.of("audiencia_id",4)),usuario));
  assertThrows(ExcepcionApi.class,()->servicio.resolverAudiencias(datos(Map.of("audiencia_id",99)),usuario));
 }
 @Test void noOmiteElPermisoDeConsulta(){
  when(administrador.listar("audiencias",usuario)).thenThrow(new ExcepcionApi(HttpStatus.FORBIDDEN,"SIN_PERMISO","Sin permiso"));
  assertEquals(HttpStatus.FORBIDDEN,assertThrows(ExcepcionApi.class,()->servicio.resolverAudiencias(datos(Map.of("audiencia_id",4)),usuario)).getEstadoHttp());
 }
 @Test void planNuevoPagadoExigeSeleccionYElLegadoConservaSuTexto(){
  assertThrows(ExcepcionApi.class,()->servicio.resolverAudiencias(datos(Map.of("audiencia_id","","modalidad","PAGADA","plataforma","FACEBOOK")),usuario));
  var d=datos(Map.of("audiencia","Audiencia histórica","modalidad","PAGADA"));servicio.resolverAudiencias(d,usuario);assertEquals("Audiencia histórica",canal(d).get("audiencia"));verifyNoInteractions(administrador);
  servicio.resolverAudiencias(datos(Map.of("audiencia_id","","modalidad","ORGANICA")),usuario);verifyNoInteractions(administrador);
 }
}
