package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrmMockarooTests {
    @Test void agrupaAnunciosSinMezclarFilasDesordenadasYSinEnviarIds(){
        var http=mock(CrmProveedorHttp.class);var servicio=new CrmMockarooServicio(http);
        ReflectionTestUtils.setField(servicio,"habilitado",true);ReflectionTestUtils.setField(servicio,"apiKey","clave-prueba");
        var filas=respuesta(18);for(int i=9;i<18;i++)((com.fasterxml.jackson.databind.node.ObjectNode)filas.get(i)).put("impresiones",4000);
        ArrayNode invertida=new ObjectMapper().createArrayNode();for(int i=17;i>=0;i--)invertida.add(filas.get(i));
        when(http.post(anyString(),anyMap(),any(),eq(false))).thenReturn(invertida);
        LocalDate fecha=LocalDate.of(2026,1,1);Map<Long,List<LocalDate>> solicitud=new LinkedHashMap<>();solicitud.put(101L,List.of(fecha));solicitud.put(202L,List.of(fecha));
        var datos=servicio.generarAnuncios(solicitud);assertEquals(9,datos.get(101L).size());assertTrue(datos.get(101L).stream().allMatch(f->f.impresiones()==2000));assertTrue(datos.get(202L).stream().allMatch(f->f.impresiones()==4000));
        verify(http).post(eq("https://api.mockaroo.com/api/generate.json?count=18&array=true"),anyMap(),eq(CrmMockarooServicio.campos()),eq(false));
    }
    @Test void divideLaCargaInicialEnDescargasDeMenosDeMilFilas(){
        var http=mock(CrmProveedorHttp.class);var servicio=new CrmMockarooServicio(http);
        ReflectionTestUtils.setField(servicio,"habilitado",true);ReflectionTestUtils.setField(servicio,"apiKey","clave-prueba");
        when(http.post(anyString(),anyMap(),any(),eq(false))).thenAnswer(inv->{String url=inv.getArgument(0);int n=Integer.parseInt(url.split("count=")[1].split("&")[0]);assertTrue(n<=1000);return respuesta(n);});
        Map<Long,List<LocalDate>> solicitud=new LinkedHashMap<>();for(long i=1;i<=112;i++)solicitud.put(i,List.of(LocalDate.of(2026,1,1)));
        var datos=servicio.generarAnuncios(solicitud);assertEquals(112,datos.size());assertTrue(datos.values().stream().allMatch(f->f.size()==9));verify(http,times(2)).post(anyString(),anyMap(),any(),eq(false));
    }
    static ArrayNode respuesta(int n){ArrayNode a=new ObjectMapper().createArrayNode();for(int i=1;i<=n;i++)a.addObject().put("n",i).put("impresiones",2000).put("ctr_base",300).put("conversion_base",500).put("costo_centavos",50).put("ticket_centavos",2000);return a;}
    @Test void generaDatosCoherentesPorDiaYPaisSinSuperarMilFilas(){
        LocalDate d=LocalDate.of(2026,1,1);var dias=d.datesUntil(d.plusDays(90)).toList();var r=CrmMockarooServicio.interpretar(respuesta(810),dias);
        assertEquals(810,r.size());assertEquals(810,r.stream().map(f->f.fecha()+f.pais()).distinct().count());
        var fila=r.getFirst();assertEquals(60,fila.clics());assertEquals(3,fila.conversiones());assertEquals(new BigDecimal("30.00"),fila.inversion());assertEquals(new BigDecimal("60.00"),fila.ingresos());
    }
    @Test void rechazaDatosParcialesDuplicadosYValoresInvalidos(){
        var dias=List.of(LocalDate.of(2026,1,1));assertThrows(ExcepcionApi.class,()->CrmMockarooServicio.interpretar(respuesta(8),dias));
        var duplicado=respuesta(9);duplicado.set(1,duplicado.get(0));assertThrows(ExcepcionApi.class,()->CrmMockarooServicio.interpretar(duplicado,dias));
        var malo=respuesta(9);((com.fasterxml.jackson.databind.node.ObjectNode)malo.get(0)).put("impresiones",-1);assertThrows(ExcepcionApi.class,()->CrmMockarooServicio.interpretar(malo,dias));
    }
    @Test void noConsultaAlProveedorSinConfiguracion(){
        CrmProveedorHttp http=mock(CrmProveedorHttp.class);var s=new CrmMockarooServicio(http);ReflectionTestUtils.setField(s,"apiKey","");assertFalse(s.configurado());assertThrows(ExcepcionApi.class,()->s.generar(List.of(LocalDate.now())));verifyNoInteractions(http);
    }
    @Test void ratiosSeCalculanConTotalesYNoConPromediosDePorcentajes(){
        List<Map<String,Object>>filas=List.of(Map.of("impresiones",1000,"clics",100,"conversiones",10,"inversion",50,"ingresos",200),Map.of("impresiones",9000,"clics",0,"conversiones",0,"inversion",0,"ingresos",0));
        var r=CrmPublicidadDemoServicio.resumen(filas);assertEquals(new BigDecimal("1.0000"),r.get("ctr"));assertEquals(new BigDecimal("0.5000"),r.get("cpc"));assertEquals(new BigDecimal("5.0000"),r.get("cpm"));assertEquals(new BigDecimal("4.0000"),r.get("roas"));
        assertNull(CrmPublicidadDemoServicio.resumen(List.of()).get("cpm"));
    }
    @Test void periodoEsInclusivoDeHastaNoventaDias(){
        LocalDate d=LocalDate.of(2026,1,1);assertDoesNotThrow(()->CrmPublicidadDemoServicio.periodo(d,d.plusDays(89)));assertThrows(ExcepcionApi.class,()->CrmPublicidadDemoServicio.periodo(d,d.plusDays(90)));assertThrows(ExcepcionApi.class,()->CrmPublicidadDemoServicio.periodo(d,d.minusDays(1)));
    }
}
