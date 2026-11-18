package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

/** Verificación HTTP sin base de datos ni red: la suscripción no depende de la recepción. */
class CrmWhatsAppVerificacionTests {
    private static final String TOKEN="solo-pruebas-verificacion-1234567890";
    private CrmWhatsAppServicio servicio;
    @BeforeEach void configurar(){
        servicio=new CrmWhatsAppServicio(null,null,null,null,null);
        ReflectionTestUtils.setField(servicio,"verifyToken",TOKEN);
        ReflectionTestUtils.setField(servicio,"phoneId","");
        ReflectionTestUtils.setField(servicio,"appSecret","");
    }
    @Test void confirmaElDesafioAunqueLaRecepcionTodaviaNoEsteConfigurada(){
        assertFalse(servicio.configurado());
        var respuesta=new CrmWhatsAppControlador(servicio).verificar("subscribe",TOKEN,"123456");
        assertEquals(200,respuesta.getStatusCode().value());assertEquals("123456",respuesta.getBody());assertEquals("no-store",respuesta.getHeaders().getCacheControl());
        ExcepcionApi error=assertThrows(ExcepcionApi.class,()->servicio.validarFirma(new byte[0],null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getEstadoHttp());
        assertTrue(error.getMessage().contains("CRM_WHATSAPP_APP_SECRET"));assertTrue(error.getMessage().contains("CRM_WHATSAPP_PHONE_ID"));assertFalse(error.getMessage().contains(TOKEN));
    }
    @Test void rechazaTokensDistintosYSolicitudesSinParametros(){
        ExcepcionApi distinto=assertThrows(ExcepcionApi.class,()->servicio.verificar("subscribe","otro-token","123456"));
        assertEquals(HttpStatus.FORBIDDEN,distinto.getEstadoHttp());assertEquals("WHATSAPP_VERIFICACION",distinto.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ExcepcionApi.class,()->servicio.verificar(null,null,null)).getEstadoHttp());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ExcepcionApi.class,()->servicio.verificar("subscribe",TOKEN,null)).getEstadoHttp());
    }
    @Test void explicaElTokenDeVerificacionAusenteOInvalido(){
        for(String valor:new String[]{"","corto","x".repeat(257)}){
            ReflectionTestUtils.setField(servicio,"verifyToken",valor);
            ExcepcionApi error=assertThrows(ExcepcionApi.class,()->servicio.verificar("subscribe",TOKEN,"123456"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getEstadoHttp());
            assertTrue(error.getMessage().contains("CRM_WHATSAPP_VERIFY_TOKEN"));assertFalse(error.getMessage().contains(TOKEN));
        }
    }
}
