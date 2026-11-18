package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JWT y SMTP simulados; consultas, permisos y transacciones PostgreSQL reales, con rollback. */
@SpringBootTest(properties={"spring.mail.host=smtp.example.test","crm.public-url=https://crm.example.test","crm.smtp.from=marketing@example.test","crm.smtp.one-click=true","crm.automatizaciones.habilitadas=false","crm.google.client-id=test","crm.google.client-secret=test","crm.google.refresh-token=test","crm.google.developer-token=","crm.whatsapp.token=test","crm.whatsapp.app-secret=secreto-app-solo-pruebas-no-real","crm.whatsapp.verify-token=verificacion-solo-pruebas-1234567890","crm.whatsapp.phone-id=123456","crm.twilio.account-sid=AC00000000000000000000000000000000","crm.twilio.auth-token=test","crm.twilio.from=+15005550006"})
@AutoConfigureMockMvc @Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CRM_PRUEBAS_BD",matches="true")
class CrmEvolucionPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CrmServicio contactos;
    @Autowired CrmOperacionServicio operaciones;
    @Autowired CrmEvolucionServicio evolucion;
    @Autowired CrmIntegraciones integraciones;
    @Autowired CrmBajasServicio bajas;
    @Autowired CrmCamposServicio campos;
    @Autowired CrmFlujosServicio flujos;
    @Autowired CrmGoogleServicio google;
    @Autowired CrmMensajeriaServicio mensajeria;
    @Autowired CrmWhatsAppServicio whatsapp;
    @MockitoBean CrmProveedorHttp http;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JavaMailSender correo;
    UUID admin,lider,analista;
    long usuarioAdmin,usuarioLider,usuarioAnalista,campana;
    @BeforeEach void usuarios(){
        admin=UUID.randomUUID();lider=UUID.randomUUID();analista=UUID.randomUUID();
        usuarioAdmin=usuario("Administrador",admin);usuarioLider=usuario("Líder",lider);usuarioAnalista=usuario("Analista",analista);
        campana=jdbc.queryForObject("select id_campana from public.campana order by id_campana limit 1",Long.class);
        when(decoder.decode(anyString())).thenAnswer(inv->Jwt.withTokenValue(inv.getArgument(0)).subject(inv.getArgument(0)).header("alg","ES256").build());
    }
    private long usuario(String nombre,UUID sujeto){
        long rol=jdbc.queryForObject("insert into public.rol(nombre_rol,descripcion) values(?,?) returning id_rol",Long.class,nombre+" test "+sujeto.toString().substring(0,8),"Prueba temporal rollback");
        // Conceder todos los códigos demuestra que el límite por rol no se puede eludir.
        jdbc.update("insert into public.rol_permiso select ?,id_permiso from public.permiso where modulo='CRM'",rol);
        return jdbc.queryForObject("insert into public.usuario(nombres,correo,estado,id_rol,id_supabase,debe_cambiar_password) values(?,?,'ACTIVO',?,?,false) returning id_usuario",Long.class,"Prueba rollback",sujeto+"@example.test",rol,sujeto);
    }
    private long contacto(){return ((Number)contactos.guardar("contactos",null,new CrmControlador.Registro("Contacto prueba rollback",UUID.randomUUID()+"@example.test",null,"NUEVO","PRUEBA", " Premium ,premium,antigua",null,campana,usuarioAdmin,null,null,null),admin).get("id")).longValue();}
    private JsonNode solicitud(UUID sujeto,String metodo,String ruta,Object cuerpo,int esperado)throws Exception{
        var r=switch(metodo){case "POST"->post(ruta);case "PUT"->put(ruta);case "DELETE"->delete(ruta);default->get(ruta);};
        r.header("Authorization","Bearer "+sujeto);if(cuerpo!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo));
        var respuesta=mvc.perform(r).andExpect(status().is(esperado)).andReturn().getResponse();
        return respuesta.getContentAsByteArray().length==0?json.nullNode():json.readTree(respuesta.getContentAsByteArray());
    }
    private static final String APP_SECRET_PRUEBA="secreto-app-solo-pruebas-no-real";
    private static final String VERIFY_PRUEBA="verificacion-solo-pruebas-1234567890";
    private byte[]eventoWhatsApp(String phone,List<?>mensajes,List<?>estados)throws Exception{return json.writeValueAsBytes(Map.of("object","whatsapp_business_account","entry",List.of(Map.of("changes",List.of(Map.of("field","messages","value",Map.of("metadata",Map.of("phone_number_id",phone),"messages",mensajes,"statuses",estados)))))));}
    private String firmaWhatsApp(byte[]cuerpo)throws Exception{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(APP_SECRET_PRUEBA.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));return "sha256="+HexFormat.of().formatHex(mac.doFinal(cuerpo));}
    private void recibirWhatsApp(byte[]cuerpo)throws Exception{mvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).header("X-Hub-Signature-256",firmaWhatsApp(cuerpo)).content(cuerpo)).andExpect(status().isOk());}
    @Test void webhookExigeFirmaDelCuerpoYVerificaDesafio()throws Exception{
        mvc.perform(get("/api/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token",VERIFY_PRUEBA).param("hub.challenge","desafio-123")).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("desafio-123"));
        mvc.perform(get("/api/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token","incorrecto").param("hub.challenge","123")).andExpect(status().isForbidden());
        byte[]cuerpo=eventoWhatsApp("123456",List.of(),List.of());
        mvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).header("X-Hub-Signature-256",firmaWhatsApp(cuerpo)).content(new String(cuerpo,java.nio.charset.StandardCharsets.UTF_8)+" ")).andExpect(status().isForbidden());
        recibirWhatsApp(cuerpo);
        mvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(new byte[CrmWhatsAppControlador.MAX_CUERPO+1])).andExpect(status().isPayloadTooLarge());
        org.springframework.test.util.ReflectionTestUtils.setField(whatsapp,"appSecret","");try{
            mvc.perform(get("/api/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token",VERIFY_PRUEBA).param("hub.challenge","456")).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("456"));
            mvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).header("X-Hub-Signature-256",firmaWhatsApp(cuerpo)).content(cuerpo)).andExpect(status().isServiceUnavailable()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("CRM_WHATSAPP_APP_SECRET")));
        }finally{org.springframework.test.util.ReflectionTestUtils.setField(whatsapp,"appSecret",APP_SECRET_PRUEBA);}
    }
    @Test void mensajesEntrantesDeduplicadosAsociacionPermisosYConsentimiento()throws Exception{
        long c=contacto();String telefono="51"+java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000,999999999);jdbc.update("update public.crm_contacto set telefono=? where id=?",telefono,c);String id="wamid.test."+UUID.randomUUID(),bsuid="US."+UUID.randomUUID(),segundo="wamid.test."+UUID.randomUUID();String ahora=Long.toString(Instant.now().getEpochSecond());
        byte[]cuerpo=eventoWhatsApp("123456",List.of(Map.of("id",id,"from",telefono,"timestamp",ahora,"type","text","text",Map.of("body","<img src=x onerror=alert(1)>")),Map.of("id",segundo,"from_user_id",bsuid,"timestamp",ahora,"type","image")),List.of());
        recibirWhatsApp(cuerpo);recibirWhatsApp(cuerpo);
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_whatsapp_mensaje where proveedor_id=?",Integer.class,id));assertEquals(c,jdbc.queryForObject("select contacto_id from public.crm_whatsapp_mensaje where proveedor_id=?",Long.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from public.crm_consentimiento where contacto_id=?",Integer.class,c));
        var recibidos=solicitud(analista,"GET","/api/crm/contactos/"+c+"/whatsapp",null,200).path("datos");assertEquals(1,recibidos.path("total").asInt());assertTrue(recibidos.path("filas").get(0).path("texto").asText().contains("<img"));
        solicitud(lider,"GET","/api/crm/operaciones/whatsapp/mensajes",null,403);solicitud(analista,"GET","/api/crm/operaciones/whatsapp/estado",null,403);solicitud(admin,"GET","/api/crm/operaciones/whatsapp/mensajes?pendientes=true",null,200);
        long pendiente=jdbc.queryForObject("select id from public.crm_whatsapp_mensaje where proveedor_id=? and contacto_id is null",Long.class,segundo);String ruta="/api/crm/operaciones/whatsapp/mensajes/"+pendiente+"/contacto";
        solicitud(analista,"PUT",ruta,Map.of("contactoId",c,"version",0),403);solicitud(admin,"PUT",ruta,Map.of("contactoId",c,"version",0),200);solicitud(admin,"PUT",ruta,Map.of("contactoId",c,"version",0),409);recibirWhatsApp(cuerpo);assertEquals(c,jdbc.queryForObject("select contacto_id from public.crm_whatsapp_mensaje where id=?",Long.class,pendiente));
        recibirWhatsApp(eventoWhatsApp("999999",List.of(Map.of("id","no-guardar","from",telefono,"timestamp",ahora,"type","text","text",Map.of("body","De otro número"))),List.of()));assertEquals(0,jdbc.queryForObject("select count(*) from public.crm_whatsapp_mensaje where proveedor_id='no-guardar'",Integer.class));
    }
    @Test void estadosVerificadosAntesDeAceptacionYFueraDeOrdenNoRetroceden()throws Exception{
        long c=contacto();jdbc.update("update public.crm_contacto set telefono=? where id=?","51"+java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000,999999999),c);jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,'MARKETING','WHATSAPP',now(),?)",c,usuarioAdmin);
        String id="wamid.entrega."+UUID.randomUUID(),ahora=Long.toString(Instant.now().getEpochSecond());
        // La confirmación puede llegar antes de que la llamada de envío devuelva su ID.
        when(http.post(contains("graph.facebook"),anyMap(),any(),eq(false))).thenAnswer(inv->{recibirWhatsApp(eventoWhatsApp("123456",List.of(),List.of(Map.of("id",id,"status","read","timestamp",ahora))));return json.readTree("{\"messages\":[{\"id\":\""+id+"\"}]}");});
        long flujo=flujoMensajeria(c,"WHATSAPP",Map.of("plantilla","bienvenida","idioma","es","parametros","{{nombre}}\n{{baja}}"));integraciones.ejecutar(flujo,admin);long e=jdbc.queryForObject("select id from public.crm_ejecucion where automatizacion_id=?",Long.class,flujo);
        byte[]tardio=eventoWhatsApp("123456",List.of(),List.of(Map.of("id",id,"status","sent","timestamp",ahora),Map.of("id",id,"status","delivered","timestamp",ahora),Map.of("id",id,"status","failed","timestamp",ahora,"errors",List.of(Map.of("code",131026))),Map.of("id",id,"status","read","timestamp",ahora)));
        recibirWhatsApp(tardio);recibirWhatsApp(tardio);assertEquals(4,jdbc.queryForObject("select count(*) from public.crm_whatsapp_estado where proveedor_id=?",Integer.class,id));
        assertEquals("READ",flujos.historial(flujo,e,admin).stream().filter(p->p.get("proveedor_id")!=null).findFirst().orElseThrow().get("entrega"));assertEquals("COMPLETADO",jdbc.queryForObject("select estado from public.crm_ejecucion where id=?",String.class,e));
        assertEquals("123456",jdbc.queryForObject("select phone_id from public.crm_envio where ejecucion_id=?",String.class,e));assertTrue((Boolean)whatsapp.estado(admin).get("configurado"));
        String fallido="wamid.fallo."+UUID.randomUUID();recibirWhatsApp(eventoWhatsApp("123456",List.of(),List.of(Map.of("id",fallido,"status","failed","timestamp",ahora,"errors",List.of(Map.of("code",131026))))));assertEquals("FAILED",jdbc.queryForObject("select estado from public.crm_whatsapp_entrega where proveedor_id=?",String.class,fallido));assertEquals("131026",jdbc.queryForObject("select codigo_error from public.crm_whatsapp_entrega where proveedor_id=?",String.class,fallido));
    }
    @Test void matrizRolesPropiedadYFlujoDeAprobacion()throws Exception{
        mvc.perform(get("/api/crm/contactos")).andExpect(status().isUnauthorized());
        long c=contacto();
        for(UUID u:List.of(admin,lider,analista))solicitud(u,"GET","/api/crm/contactos/"+c,null,200);
        solicitud(analista,"GET","/api/crm/cuentas",null,403);
        solicitud(analista,"GET","/api/crm/operaciones/pipeline",null,403);
        solicitud(lider,"GET","/api/crm/operaciones/integraciones",null,403);
        solicitud(analista,"GET","/api/crm/operaciones/auditoria",null,403);
        solicitud(admin,"GET","/api/crm/operaciones/auditoria",null,200);
        long tag=((Number)evolucion.etiqueta(Map.of("nombre","Tag "+UUID.randomUUID(),"color","#123456"),lider).get("id")).longValue();
        solicitud(analista,"PUT","/api/crm/operaciones/contactos/"+c+"/etiquetas",List.of(tag),403);
        solicitud(lider,"PUT","/api/crm/operaciones/contactos/"+c+"/etiquetas",List.of(tag),200);
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_contacto_etiqueta where contacto_id=?",Integer.class,c));
        var propia=new HashMap<String,Object>(Map.of("nombre","Tarea propia prueba","tipo","LLAMADA","estado","PENDIENTE","fecha",OffsetDateTime.now().minusHours(1).toString(),"responsable_id",usuarioAnalista,"contacto_id",c));
        long tarea=solicitud(analista,"POST","/api/crm/operaciones/actividades",propia,201).path("datos").path("id").asLong();
        long ajena=((Number)operaciones.guardar("actividades",null,new HashMap<>(Map.of("nombre","Tarea ajena prueba","tipo","REUNION","estado","PENDIENTE","fecha",OffsetDateTime.now().toString(),"responsable_id",usuarioLider,"contacto_id",c)),lider).get("id")).longValue();
        propia.put("version",0);propia.put("estado","COMPLETADA");solicitud(analista,"PUT","/api/crm/operaciones/actividades/"+tarea,propia,200);
        solicitud(analista,"PUT","/api/crm/operaciones/actividades/"+ajena,propia,403);
        JsonNode filas=solicitud(analista,"GET","/api/crm/operaciones/actividades",null,200).path("datos").path("filas");
        assertTrue(filas.size()>0);for(JsonNode f:filas)assertEquals(usuarioAnalista,f.path("responsable_id").asLong());
        var pieza=new HashMap<String,Object>(Map.of("nombre","Pieza prueba","responsable_id",usuarioAnalista,"estado","BORRADOR","texto","Texto prueba"));
        long contenido=solicitud(analista,"POST","/api/crm/operaciones/contenido",pieza,201).path("datos").path("id").asLong();
        pieza.put("version",0);pieza.put("estado","EN_REVISION");solicitud(analista,"PUT","/api/crm/operaciones/contenido/"+contenido,pieza,200);
        pieza.put("version",1);pieza.put("estado","APROBADO");solicitud(analista,"PUT","/api/crm/operaciones/contenido/"+contenido,pieza,403);solicitud(lider,"PUT","/api/crm/operaciones/contenido/"+contenido,pieza,200);
    }
    @Test void googleMetricasSinDuplicadosPermisosYErrorConservaHistorico()throws Exception{
        campana=nuevaCampana();LocalDate dia=LocalDate.now(ZoneOffset.UTC).minusDays(1);
        when(http.post(anyString(),anyMap(),any(),anyBoolean())).thenAnswer(inv->{String url=inv.getArgument(0);if(url.contains("oauth2"))return json.readTree("{\"access_token\":\"fake-only-test\",\"expires_in\":3600}");if(url.contains("googleads"))return json.readTree("[{\"results\":[{\"campaign\":{\"id\":\"22\"},\"customer\":{\"currencyCode\":\"PEN\"},\"segments\":{\"date\":\""+dia+"\"},\"metrics\":{\"costMicros\":\"12345678\",\"impressions\":\"100\",\"clicks\":\"4\"}}]}]");return json.readTree("{\"rowCount\":1,\"rows\":[{\"dimensionValues\":[{\"value\":\""+dia.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)+"\"}],\"metricValues\":[{\"value\":\"5\"},{\"value\":\"6\"},{\"value\":\"1.5\"}]}]}");});
        long ads=solicitud(admin,"POST","/api/crm/operaciones/conectores",Map.of("fuente","GOOGLE_ADS","cuenta_externa","123","campana_externa","22","campana_id",campana),201).path("datos").path("id").asLong();
        solicitud(lider,"GET","/api/crm/operaciones/conectores",null,403);solicitud(analista,"POST","/api/crm/operaciones/conectores/"+ads+"/sincronizar?desde="+dia+"&hasta="+dia,null,403);
        assertTrue((Boolean)google.estado(admin).get("googleAdsConfigurado"));
        google.sincronizar(ads,dia,dia,admin);google.sincronizar(ads,dia,dia,admin);
        verify(http,times(2)).post(contains("googleads"),argThat(h->h.containsKey("Authorization")&&!h.containsKey("developer-token")),any(),eq(false));
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_gasto_google where conector_id=?",Integer.class,ads));
        assertEquals(0,new BigDecimal("12.345678").compareTo(jdbc.queryForObject("select importe from public.crm_gasto_google where conector_id=?",BigDecimal.class,ads)));
        @SuppressWarnings("unchecked") var gastos=(List<Map<String,Object>>)operaciones.atribucion(dia,dia,"PRIMERO",admin).get("gastos");assertEquals(0,new BigDecimal("12.345678").compareTo(new BigDecimal(gastos.stream().filter(f->Objects.equals(f.get("campana_id"),campana)).findFirst().orElseThrow().get("gasto").toString())));
        long ga=((Number)google.crear(Map.of("fuente","GA4","cuenta_externa","321","campana_externa","newsletter","campana_id",campana),admin).get("id")).longValue();google.sincronizar(ga,dia,dia,admin);google.sincronizar(ga,dia,dia,admin);assertEquals(1,google.metricas(ga,dia,dia,admin).size());assertEquals(6L,google.metricas(ga,dia,dia,admin).getFirst().get("sesiones"));
        when(http.post(contains("googleads"),anyMap(),any(),anyBoolean())).thenThrow(CrmOperacionServicio.invalido("Proveedor rechazó operación"));assertThrows(com.marketing.agencia.config.ExcepcionApi.class,()->google.sincronizar(ads,dia,dia,admin));assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_gasto_google where conector_id=?",Integer.class,ads));
        google.activar(ads,0,false,admin);assertThrows(com.marketing.agencia.config.ExcepcionApi.class,()->google.sincronizar(ads,dia,dia,admin));solicitud(admin,"PUT","/api/crm/operaciones/conectores/"+ads,Map.of("version",0,"activo",true),409);
    }
    @Test void mensajeriaConsentimientoBajaPorCanalYResultadoInciertoNoReintenta()throws Exception{
        long c=contacto();jdbc.update("update public.crm_contacto set telefono='51987654321' where id=?",c);for(String canal:List.of("SMS","WHATSAPP"))jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,'MARKETING',?,now(),?)",c,canal,usuarioAdmin);
        when(http.post(contains("twilio"),anyMap(),any(),eq(true))).thenReturn(json.readTree("{\"sid\":\"SM-test-only\",\"status\":\"queued\"}"));
        long sms=flujoMensajeria(c,"SMS",Map.of("mensaje","Hola {{nombre}}"));integraciones.ejecutar(sms,admin);integraciones.ejecutar(sms,admin);
        verify(http,times(1)).post(contains("twilio"),anyMap(),argThat(o->o instanceof Map<?,?> m&&m.get("To").equals("+51987654321")&&m.get("Body").toString().contains("Baja: https://crm.example.test/api/bajas/")),eq(true));
        assertEquals("ACEPTADO",jdbc.queryForObject("select estado from public.crm_envio where ejecucion_id=(select id from public.crm_ejecucion where automatizacion_id=?)",String.class,sms));
        String enlace=bajas.enlace(c,"SMS");String baja=enlace.substring(enlace.lastIndexOf('/')+1);mvc.perform(get("/api/bajas/"+baja)).andExpect(status().isOk());assertTrue(mensajeria.consentido(c,"SMS"));mvc.perform(post("/api/bajas/"+baja)).andExpect(status().isOk());assertFalse(mensajeria.consentido(c,"SMS"));assertTrue(mensajeria.consentido(c,"WHATSAPP"));
        jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,'MARKETING','SMS',now(),?)",c,usuarioAdmin);assertFalse(mensajeria.consentido(c,"SMS"));integraciones.ejecutar(flujoMensajeria(c,"SMS",Map.of("mensaje","No debe enviarse")),admin);verify(http,times(1)).post(contains("twilio"),anyMap(),any(),anyBoolean());
        assertEquals(List.of("SMS"),evolucion.relaciones(c,admin).get("canalesSuprimidos"));
        long ejecucionSms=jdbc.queryForObject("select id from public.crm_ejecucion where automatizacion_id=?",Long.class,sms);assertEquals("SM-test-only",flujos.historial(sms,ejecucionSms,admin).stream().filter(p->p.get("proveedor_id")!=null).findFirst().orElseThrow().get("proveedor_id"));
        Map<String,Object>plantilla=Map.of("plantilla","bienvenida","idioma","es","parametros","{{nombre}}\n{{baja}}");
        when(http.post(contains("graph.facebook"),anyMap(),any(),eq(false))).thenReturn(json.readTree("{\"messages\":[{\"id\":\"wamid.test\"}]}"));
        long wa=flujoMensajeria(c,"WHATSAPP",plantilla);integraciones.ejecutar(wa,admin);assertEquals("ACEPTADO",jdbc.queryForObject("select estado from public.crm_envio where ejecucion_id=(select id from public.crm_ejecucion where automatizacion_id=?)",String.class,wa));
        when(http.post(contains("graph.facebook"),anyMap(),any(),eq(false))).thenThrow(CrmOperacionServicio.invalido("Respuesta incierta de prueba"));long incierto=flujoMensajeria(c,"WHATSAPP",plantilla);integraciones.ejecutar(incierto,admin);integraciones.ejecutar(incierto,admin);assertEquals("INCIERTO",jdbc.queryForObject("select estado from public.crm_ejecucion where automatizacion_id=?",String.class,incierto));verify(http,times(2)).post(contains("graph.facebook"),anyMap(),any(),eq(false));
    }
    private long flujoMensajeria(long c,String canal,Map<String,Object>parametros){
        String origen="msg-"+UUID.randomUUID();jdbc.update("update public.crm_contacto set origen=? where id=?",origen,c);long s=((Number)operaciones.guardar("segmentos",null,Map.of("nombre","Mensajería prueba","origen",origen),admin).get("id")).longValue();
        long f=((Number)operaciones.guardar("automatizaciones",null,Map.of("nombre","Mensaje prueba","segmento_id",s,"accion","CREAR_TAREA","asunto","Base","activa",true),admin).get("id")).longValue();flujos.guardar(f,new CrmFlujosServicio.Edicion("Mensaje prueba",s,true,0,new CrmFlujosServicio.Definicion("entrada",List.of(new CrmFlujosServicio.Nodo("entrada","SEGMENTO","Entrada",Map.of(),"mensaje",null,null),new CrmFlujosServicio.Nodo("mensaje",canal,"Comunicación",parametros,null,null,null)))),admin);return f;
    }
    @Test void camposTipadosPermisosConcurrenciaYArchivo()throws Exception{
        long c=contacto();String codigo="rubro_"+UUID.randomUUID().toString().substring(0,8);
        long campo=solicitud(admin,"POST","/api/crm/campos",Map.of("codigo",codigo,"nombre","Rubro prueba","tipo","OPCION","opciones",List.of("A","B"),"activo",true),201).path("datos").path("id").asLong();
        solicitud(lider,"GET","/api/crm/campos",null,403);solicitud(analista,"GET","/api/crm/campos",null,403);
        solicitud(lider,"PUT","/api/crm/campos/contactos/"+c,Map.of("version",0,"valores",Map.of(codigo,"B")),200);
        assertEquals("B",solicitud(analista,"GET","/api/crm/campos/contactos/"+c,null,200).path("datos").path("valores").path(codigo).asText());
        solicitud(analista,"PUT","/api/crm/campos/contactos/"+c,Map.of("version",1,"valores",Map.of(codigo,"A")),403);
        solicitud(lider,"PUT","/api/crm/campos/contactos/"+c,Map.of("version",0,"valores",Map.of(codigo,"A")),409);
        solicitud(admin,"PUT","/api/crm/campos/"+campo,Map.of("codigo",codigo,"nombre","Rubro prueba","tipo","OPCION","opciones",List.of("A"),"activo",true,"version",0),400);
        solicitud(admin,"PUT","/api/crm/campos/"+campo,Map.of("codigo",codigo,"nombre","Rubro prueba","tipo","OPCION","opciones",List.of("A","B"),"activo",false,"version",0),200);
        assertFalse(((Map<?,?>)campos.valores(c,admin).get("valores")).containsKey(codigo));
        campos.guardar(campo,Map.of("codigo",codigo,"nombre","Rubro prueba","tipo","OPCION","opciones",List.of("A","B"),"activo",true,"version",1),admin);
        assertEquals("B",((Map<?,?>)campos.valores(c,lider).get("valores")).get(codigo));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
    @Test void flujoVisualEsperaPersisteNoDuplicaYTomaSnapshot()throws Exception{
        long c=contacto();String origen="VISUAL-"+UUID.randomUUID();jdbc.update("update public.crm_contacto set origen=? where id=?",origen,c);
        long segmento=((Number)operaciones.guardar("segmentos",null,Map.of("nombre","Segmento visual prueba","origen",origen),admin).get("id")).longValue();
        long flujo=((Number)operaciones.guardar("automatizaciones",null,Map.of("nombre","Flujo visual prueba","segmento_id",segmento,"accion","CREAR_TAREA","asunto","Inicial","activa",true),admin).get("id")).longValue();
        var definicion=new CrmFlujosServicio.Definicion("inicio",List.of(new CrmFlujosServicio.Nodo("inicio","SEGMENTO","Inicio",Map.of(),"condicion",null,null),new CrmFlujosServicio.Nodo("condicion","CONDICION","Solo nuevos",Map.of("campo","ESTADO","valor","NUEVO"),null,"espera",null),new CrmFlujosServicio.Nodo("espera","ESPERA","Esperar",Map.of("minutos",1),"tarea",null,null),new CrmFlujosServicio.Nodo("tarea","TAREA","Tarea",Map.of("asunto","Original","mensaje","Mensaje"),null,null,null)));
        flujos.guardar(flujo,new CrmFlujosServicio.Edicion("Flujo visual prueba",segmento,true,0,definicion),lider);
        solicitud(analista,"GET","/api/crm/operaciones/automatizaciones/"+flujo+"/flujo",null,403);
        solicitud(lider,"GET","/api/crm/operaciones/automatizaciones",null,200);
        integraciones.ejecutar(flujo,admin);assertEquals(0,jdbc.queryForObject("select count(*) from public.crm_actividad where contacto_id=?",Integer.class,c));
        assertNotNull(jdbc.queryForObject("select reanudar_en from public.crm_ejecucion where automatizacion_id=? and contacto_id=?",OffsetDateTime.class,flujo,c));
        integraciones.ejecutar(flujo,admin);assertEquals(0,jdbc.queryForObject("select count(*) from public.crm_actividad where contacto_id=?",Integer.class,c));
        List<CrmFlujosServicio.Nodo>modificados=new ArrayList<>(definicion.nodos());modificados.set(3,new CrmFlujosServicio.Nodo("tarea","TAREA","Tarea",Map.of("asunto","Cambio posterior","mensaje","Otro"),null,null,null));
        flujos.guardar(flujo,new CrmFlujosServicio.Edicion("Flujo visual prueba",segmento,true,1,new CrmFlujosServicio.Definicion("inicio",modificados)),admin);
        jdbc.update("update public.crm_ejecucion set reanudar_en=now()-interval '1 minute' where automatizacion_id=?",flujo);
        integraciones.ejecutar(flujo,admin);integraciones.ejecutar(flujo,admin);
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_actividad where contacto_id=? and nombre='Original'",Integer.class,c));
        long ejecucion=jdbc.queryForObject("select id from public.crm_ejecucion where automatizacion_id=? and contacto_id=?",Long.class,flujo,c);
        assertEquals(4,flujos.historial(flujo,ejecucion,admin).size());
        assertEquals("COMPLETADO",jdbc.queryForObject("select estado from public.crm_ejecucion where id=?",String.class,ejecucion));
    }
    @Test void origenesAtribucionConservaIngresosYTransicionesNoSeDuplican(){
        campana=nuevaCampana();long segunda=nuevaCampana();
        long c=contacto();
        String etiqueta="Premium";assertEquals(2,((List<?>)evolucion.relaciones(c,admin).get("etiquetas")).size());
        long segmento=((Number)operaciones.guardar("segmentos",null,Map.of("nombre","Segmento relacional prueba","etiqueta",etiqueta),admin).get("id")).longValue();
        assertTrue(operaciones.miembros(segmento,10000,admin).stream().anyMatch(f->((Number)f.get("id")).longValue()==c));
        UUID clave=UUID.randomUUID();Map<String,Object> origen=Map.of("campana_id",segunda,"canal","EMAIL","utm_source","nurturing","idempotencia",clave.toString());
        evolucion.origen(c,origen,lider);evolucion.origen(c,origen,lider);
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_origen where idempotencia=?",Integer.class,clave));
        long abierta=jdbc.queryForObject("select id from public.crm_etapa where tipo='ABIERTA' order by orden limit 1",Long.class),ganada=jdbc.queryForObject("select id from public.crm_etapa where tipo='GANADA' order by orden limit 1",Long.class);
        var d=new HashMap<String,Object>(Map.of("nombre","Venta prueba","contacto_id",c,"etapa_id",abierta,"responsable_id",usuarioLider,"valor","120.00","moneda","PEN","probabilidad",10,"fecha_cierre",LocalDate.now().toString()));
        long o=((Number)operaciones.guardar("pipeline",null,d,lider).get("id")).longValue();d.put("version",0);d.put("etapa_id",ganada);operaciones.guardar("pipeline",o,d,lider);
        d.put("version",1);operaciones.guardar("pipeline",o,d,lider);assertEquals(2,jdbc.queryForObject("select count(*) from public.crm_transicion where oportunidad_id=?",Integer.class,o));
        evolucion.origen(c,Map.of("campana_id",segunda,"canal","WEB","idempotencia",UUID.randomUUID().toString()),lider);
        // Dos campañas nuevas aíslan la venta. La interacción posterior al cierre no tiene crédito.
        for(String modelo:List.of("PRIMERO","ULTIMO","LINEAL")){
            var ingresos=evolucion.ingresos(LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),modelo);
            BigDecimal total=ingresos.stream().filter(f->f.get("campana_id")!=null&&Set.of(campana,segunda).contains(((Number)f.get("campana_id")).longValue())).map(f->new BigDecimal(f.get("ingresos").toString())).reduce(BigDecimal.ZERO,BigDecimal::add);
            assertTrue(total.subtract(new BigDecimal("120")).abs().compareTo(new BigDecimal("0.000001"))<0);
            BigDecimal b=ingresos.stream().filter(f->Objects.equals(f.get("campana_id"),segunda)).map(f->new BigDecimal(f.get("ingresos").toString())).findFirst().orElse(BigDecimal.ZERO);
            assertEquals(0,b.compareTo(new BigDecimal(modelo.equals("PRIMERO")?"0":modelo.equals("ULTIMO")?"120":"60")));
        }
        var conv=evolucion.conversiones(LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),admin);assertFalse(((List<?>)conv.get("transiciones")).isEmpty());
    }
    private long nuevaCampana(){return jdbc.queryForObject("insert into public.campana(nombre,id_cliente,id_canal,objetivo,presupuesto_asignado,fecha_inicio,fecha_fin,estado,id_usuario_creador) select ?,id_cliente,id_canal,objetivo,0,fecha_inicio,fecha_fin,estado,? from public.campana order by id_campana limit 1 returning id_campana",Long.class,"Prueba CRM "+UUID.randomUUID().toString().substring(0,8),usuarioAdmin);}
    @Test void correoIncluyeBajaYLaSupresionPrevaleceSobreConsentimiento()throws Exception{
        long c=contacto();String origen="EMAIL-"+UUID.randomUUID();jdbc.update("update public.crm_contacto set origen=? where id=?",origen,c);
        jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,'MARKETING','EMAIL',now(),?)",c,usuarioAdmin);
        long s=((Number)operaciones.guardar("segmentos",null,Map.of("nombre","Email segmento prueba","origen",origen),admin).get("id")).longValue();
        long flujo=((Number)operaciones.guardar("automatizaciones",null,Map.of("nombre","Email flujo prueba","segmento_id",s,"accion","EMAIL","asunto","Prueba","mensaje","Hola {{nombre}}","activa",true),admin).get("id")).longValue();
        MimeMessage mensaje=new MimeMessage(Session.getInstance(new Properties()));when(correo.createMimeMessage()).thenReturn(mensaje);
        assertEquals(1,integraciones.ejecutar(flujo,admin).get("procesados"));verify(correo).send(mensaje);
        assertEquals("List-Unsubscribe=One-Click",mensaje.getHeader("List-Unsubscribe-Post",null));
        String url=mensaje.getHeader("List-Unsubscribe",null).replace("<","").replace(">","");String token=url.substring(url.lastIndexOf('/')+1);
        assertFalse(mensaje.getContent().toString().contains("null"));assertTrue(mensaje.getContent().toString().contains(url));
        mvc.perform(get("/api/bajas/"+token)).andExpect(status().isOk());assertTrue(integraciones.consentido(c));
        mvc.perform(post("/api/bajas/"+token).contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click")).andExpect(status().isOk());
        assertFalse(integraciones.consentido(c));bajas.revocar(token);assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_baja_email where contacto_id=?",Integer.class,c));
        jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,'MARKETING','EMAIL',now(),?)",c,usuarioAdmin);assertFalse(integraciones.consentido(c));
        mvc.perform(post("/api/bajas/"+"A".repeat(43))).andExpect(status().isOk());
    }
}
