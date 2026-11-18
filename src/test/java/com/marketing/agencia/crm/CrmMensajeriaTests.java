package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmMensajeriaTests {
    private Map<String,Object> prueba(){return Map.of("plantilla","hello_world","idioma","en_US","parametros","");}
    @Test void helloWorldNoRequiereParametrosYRechazaLosQueNoExisten(){
        assertDoesNotThrow(()->CrmMensajeriaServicio.validar("WHATSAPP",prueba()));
        assertDoesNotThrow(()->CrmMensajeriaServicio.validar("WHATSAPP",Map.of("plantilla","hello_world","idioma","en_US")));
        assertThrows(ExcepcionApi.class,()->CrmMensajeriaServicio.validar("WHATSAPP",Map.of("plantilla","hello_world","idioma","es","parametros","")));
        assertThrows(ExcepcionApi.class,()->CrmMensajeriaServicio.validar("WHATSAPP",Map.of("plantilla","hello_world","idioma","en_US","parametros","{{baja}}")));
    }
    @Test void otrasPlantillasSiguenExigiendoParametrosYBaja(){
        for(String parametros:List.of("","{{nombre}}"))assertThrows(ExcepcionApi.class,()->CrmMensajeriaServicio.validar("WHATSAPP",Map.of("plantilla","bienvenida","idioma","es","parametros",parametros)));
        assertDoesNotThrow(()->CrmMensajeriaServicio.validar("WHATSAPP",Map.of("plantilla","bienvenida","idioma","es","parametros","{{nombre}}\n{{baja}}")));
    }
    @Test void enviaHelloWorldSinComponentesYConservaElBloqueoSinConsentimiento()throws Exception{
        JdbcTemplate jdbc=mock(JdbcTemplate.class);CrmProveedorHttp http=mock(CrmProveedorHttp.class);CrmBajasServicio bajas=mock(CrmBajasServicio.class);
        CrmMensajeriaServicio servicio=new CrmMensajeriaServicio(jdbc,http,bajas);
        ReflectionTestUtils.setField(servicio,"whatsappToken","solo-pruebas");ReflectionTestUtils.setField(servicio,"phoneId","123456");ReflectionTestUtils.setField(servicio,"version","v27.0");
        when(bajas.configurada()).thenReturn(true);
        when(jdbc.queryForObject(contains("select exists"),eq(Boolean.class),eq(1L),eq("WHATSAPP"),eq("WHATSAPP"))).thenReturn(false);
        var nodo=new CrmFlujosServicio.Nodo("mensaje","WHATSAPP","Prueba",prueba(),null,null,null);
        assertFalse(servicio.enviar(10L,1L,nodo));verifyNoInteractions(http);
        when(jdbc.queryForObject(contains("select exists"),eq(Boolean.class),eq(1L),eq("WHATSAPP"),eq("WHATSAPP"))).thenReturn(true);
        when(jdbc.update(startsWith("insert into public.crm_envio"),eq(10L),eq("mensaje"),eq("WHATSAPP"))).thenReturn(1);
        when(jdbc.queryForMap(anyString(),eq(1L))).thenReturn(Map.of("nombre","Prueba","telefono","51900000000"));
        when(http.post(anyString(),anyMap(),any(),eq(false))).thenReturn(new ObjectMapper().readTree("{\"messages\":[{\"id\":\"wamid.solo-prueba\"}]}"));
        assertTrue(servicio.enviar(10L,1L,nodo));
        verify(http).post(eq("https://graph.facebook.com/v27.0/123456/messages"),anyMap(),argThat(o->o instanceof Map<?,?> m && m.get("template").equals(Map.of("name","hello_world","language",Map.of("code","en_US")))),eq(false));
        verify(bajas,never()).enlace(anyLong(),anyString());
    }
}
