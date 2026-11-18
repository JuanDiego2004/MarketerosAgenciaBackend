package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.*;
import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"crm.mockaroo.habilitado=true","crm.mockaroo.api-key=solo-pruebas-mockaroo","crm.automatizaciones.habilitadas=false"})
@AutoConfigureMockMvc @Transactional @EnabledIfEnvironmentVariable(named="CRM_PRUEBAS_BD",matches="true")
class CrmPublicidadDemoPostgresTests {
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired CrmPublicidadDemoServicio servicio;
    @MockitoBean CrmProveedorHttp http;@MockitoBean JwtDecoder decoder;@MockitoBean JavaMailSender correo;
    UUID admin,analista,lider;long autor,campana;
    @BeforeEach void preparar(){
        admin=UUID.randomUUID();analista=UUID.randomUUID();lider=UUID.randomUUID();autor=usuario("Administrador",admin);usuario("Analista",analista);usuario("Líder",lider);
        campana=nuevaCampana();when(decoder.decode(anyString())).thenAnswer(i->Jwt.withTokenValue(i.getArgument(0)).subject(i.getArgument(0)).header("alg","ES256").build());
        when(http.post(startsWith("https://api.mockaroo.com/"),anyMap(),any(),eq(false))).thenAnswer(i->{String u=i.getArgument(0);int cantidad=Integer.parseInt(u.substring(u.indexOf("count=")+6,u.indexOf("&array")));return CrmMockarooTests.respuesta(cantidad);});
    }
    long usuario(String rol,UUID u){long r=jdbc.queryForObject("insert into public.rol(nombre_rol,descripcion) values(?,?) returning id_rol",Long.class,rol+" test "+u.toString().substring(0,8),"Rollback");jdbc.update("insert into public.rol_permiso select ?,id_permiso from public.permiso where modulo='CRM'",r);return jdbc.queryForObject("insert into public.usuario(nombres,correo,estado,id_rol,id_supabase,debe_cambiar_password) values(?,?,'ACTIVO',?,?,false) returning id_usuario",Long.class,"Prueba demo rollback",u+"@example.test",r,u);}
    long nuevaCampana(){return jdbc.queryForObject("insert into public.campana(nombre,id_cliente,id_canal,objetivo,presupuesto_asignado,fecha_inicio,fecha_fin,estado,id_usuario_creador) select ?,id_cliente,id_canal,objetivo,0,fecha_inicio,fecha_fin,estado,? from public.campana order by id_campana limit 1 returning id_campana",Long.class,"Demo rollback "+UUID.randomUUID().toString().substring(0,8),autor);}
    long demo(){return ((Number)servicio.crear(Map.of("campana_id",campana,"plataforma","META","moneda","PEN"),admin).get("id")).longValue();}
    @Test void apiAutenticaYReservaEscrituraParaAdminPeroPermiteLectura(){
        assertThrows(ExcepcionApi.class,()->servicio.crear(Map.of("campana_id",campana,"plataforma","META","moneda","PEN"),lider));
        assertThrows(ExcepcionApi.class,()->servicio.crear(Map.of("campana_id",campana,"plataforma","META","moneda","PEN"),analista));
        long id=demo();assertEquals(1,servicio.listar(analista).stream().filter(f->((Number)f.get("id")).longValue()==id).count());
        assertFalse((boolean)servicio.estado(analista).get("gestionar"));assertFalse(json.valueToTree(servicio.estado(admin)).toString().contains("solo-pruebas-mockaroo"));
        LocalDate d=LocalDate.now().minusDays(1);assertNotNull(servicio.metricas(id,d,d,"",lider));
    }
    @Test void httpDevuelveIdYDatosSinRequerirCuentaPublicitaria()throws Exception{
        mvc.perform(get("/api/crm/demostracion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/crm/publicidad-demo/estado").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        mvc.perform(get("/api/crm/demostracion/estado").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        var r=mvc.perform(post("/api/crm/demostracion").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("campana_id",campana,"plataforma","TIKTOK","moneda","USD")))).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(r.getResponse().getContentAsByteArray()).path("datos").path("id").asLong();LocalDate d=LocalDate.now().minusDays(1);
        mvc.perform(post("/api/crm/demostracion/"+id+"/sincronizar").header("Authorization","Bearer "+analista).param("desde",d.toString()).param("hasta",d.toString())).andExpect(status().isForbidden());
        mvc.perform(get("/api/crm/demostracion/"+id+"/metricas").header("Authorization","Bearer "+analista).param("desde",d.toString()).param("hasta",d.toString())).andExpect(status().isOk());
    }
    @Test void sincronizarReutilizaDiasYNoContaminaGastoReal(){
        long id=demo();LocalDate d=LocalDate.now().minusDays(2);long antes=jdbc.queryForObject("select count(*) from public.crm_gasto_meta",Long.class);
        assertEquals(9,servicio.sincronizar(id,d,d,admin).get("filasNuevas"));assertEquals(true,servicio.sincronizar(id,d,d,admin).get("reutilizado"));
        assertEquals(9,servicio.sincronizar(id,d,d.plusDays(1),admin).get("filasNuevas"));verify(http,times(2)).post(startsWith("https://api.mockaroo.com/"),anyMap(),any(),eq(false));
        assertEquals(18,jdbc.queryForObject("select count(*) from public.crm_publicidad_demo_metrica where demo_id=?",Integer.class,id));assertEquals(antes,jdbc.queryForObject("select count(*) from public.crm_gasto_meta",Long.class));
        var todo=servicio.metricas(id,d,d.plusDays(1),"",admin);assertEquals(9,((List<?>)todo.get("paises")).size());assertEquals(2,((List<?>)todo.get("dias")).size());
        var peru=servicio.metricas(id,d,d.plusDays(1),"PE",analista);assertEquals(1,((List<?>)peru.get("paises")).size());assertEquals("3.0000",((Map<?,?>)peru.get("resumen")).get("ctr").toString());
        assertThrows(ExcepcionApi.class,()->servicio.metricas(id,d,d,"XX",admin));
    }
    @Test void proveedorIncompletoNoGuardaParcialesNiBorraHistorico(){
        long id=demo();LocalDate d=LocalDate.now().minusDays(2);servicio.sincronizar(id,d,d,admin);
        when(http.post(startsWith("https://api.mockaroo.com/"),anyMap(),any(),eq(false))).thenReturn(CrmMockarooTests.respuesta(8));assertThrows(ExcepcionApi.class,()->servicio.sincronizar(id,d.plusDays(1),d.plusDays(1),admin));
        assertEquals(9,jdbc.queryForObject("select count(*) from public.crm_publicidad_demo_metrica where demo_id=?",Integer.class,id));assertNotNull(jdbc.queryForObject("select ultimo_error from public.crm_publicidad_demo where id=?",String.class,id));
    }
    @Test void retornoUsaVentasGanadasGastosAdicionalesYNoDuplicaPautaHistorica(){
        long id=demo();LocalDate d=LocalDate.now(java.time.ZoneId.of("America/Lima"));servicio.sincronizar(id,d,d,admin);
        jdbc.update("insert into public.crm_gasto_meta(campana_id,fecha,moneda,importe,impresiones,clicks,cuenta_externa,campana_externa) values(?,?,'PEN',100,1000,10,'historica','historica')",campana,d);
        long contacto=jdbc.queryForObject("insert into public.crm_contacto(nombre,correo,estado,origen,campana_id,responsable_id) values(?,?,'NUEVO','WEB',?,?) returning id",Long.class,"Retorno rollback",UUID.randomUUID()+"@example.test",campana,autor);
        long etapa=jdbc.queryForObject("select id from public.crm_etapa where tipo='GANADA' order by orden limit 1",Long.class);
        var operaciones=new CrmOperacionServicio(jdbc,new CrmAcceso(jdbc),new CrmEvolucionServicio(jdbc,new CrmAcceso(jdbc)));
        operaciones.guardar("pipeline",null,Map.of("nombre","Venta registrada","contacto_id",contacto,"etapa_id",etapa,"responsable_id",autor,"valor","1200.00","moneda","PEN","probabilidad",100,"fecha_cierre",d.toString()),admin);
        var gasto=new HashMap<String,Object>(Map.of("campana_id",campana,"fecha",d.toString(),"moneda","PEN","concepto","Producción","importe","50.00","idempotencia",UUID.randomUUID().toString()));
        long inversion=((Number)servicio.inversion(gasto,admin).get("id")).longValue();assertEquals(inversion,servicio.inversion(gasto,admin).get("id"));
        assertThrows(ExcepcionApi.class,()->servicio.inversion(gasto,analista));
        gasto.put("importe","60.00");assertThrows(ExcepcionApi.class,()->servicio.inversion(gasto,admin));
        var r=servicio.metricas(id,d,d,"PE",admin);var negocio=(Map<?,?>)r.get("negocio");
        assertEquals(0,new java.math.BigDecimal("320.00").compareTo((java.math.BigDecimal)negocio.get("inversion")));
        assertEquals(0,new java.math.BigDecimal("1200.00").compareTo((java.math.BigDecimal)negocio.get("ingresos")));
        assertEquals(0,new java.math.BigDecimal("275.00").compareTo((java.math.BigDecimal)negocio.get("roi")));
        assertEquals(0,new java.math.BigDecimal("30.00").compareTo((java.math.BigDecimal)((Map<?,?>)r.get("resumen")).get("inversion")));
        servicio.anularInversion(inversion,0,admin);assertThrows(ExcepcionApi.class,()->servicio.anularInversion(inversion,0,admin));
        negocio=(Map<?,?>)servicio.metricas(id,d,d,"",admin).get("negocio");assertEquals(0,new java.math.BigDecimal("270.00").compareTo((java.math.BigDecimal)negocio.get("inversion")));
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_inversion where id=? and anulado",Integer.class,inversion));
    }
}
