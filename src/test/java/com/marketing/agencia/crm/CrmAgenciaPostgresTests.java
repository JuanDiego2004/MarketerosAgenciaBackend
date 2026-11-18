package com.marketing.agencia.crm;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.marketing.agencia.config.ExcepcionApi;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"crm.automatizaciones.habilitadas=false"}) @Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CRM_PRUEBAS_BD",matches="true")
class CrmAgenciaPostgresTests {
 @Autowired JdbcTemplate jdbc; @Autowired CrmAgenciaServicio agencia; @Autowired CrmOperacionServicio operaciones;
 @MockitoBean JwtDecoder jwt; @MockitoBean CrmMockarooServicio mockaroo;
 UUID admin,analista;long autor,empresa,campana,cuenta;
 @BeforeEach void preparar(){
  admin=UUID.randomUUID();analista=UUID.randomUUID();autor=usuario("Administrador",admin);usuario("Analista",analista);
  long cliente=jdbc.queryForObject("insert into cliente(nombre,estado) values('Empresa contratos rollback','ACTIVO') returning id_cliente",Long.class);
  empresa=jdbc.queryForObject("select id from crm_cuenta where cliente_id=?",Long.class,cliente);
  long canal=jdbc.queryForObject("select id_canal from campana where id_canal is not null limit 1",Long.class);
  campana=jdbc.queryForObject("insert into campana(nombre,id_cliente,id_canal,objetivo,presupuesto_asignado,fecha_inicio,fecha_fin,estado,id_usuario_creador) values('Campaña contratos',?,?,'Objetivo',1000,current_date,current_date,'PLANIFICADA',?) returning id_campana",Long.class,cliente,canal,autor);
  cuenta=((Number)agencia.guardar("cuentas-publicitarias",null,Map.of("nombre","Cuenta de prueba","empresa_id",empresa,"codigo","ACC-"+UUID.randomUUID(),"plataforma","FACEBOOK","moneda","PEN"),admin).get("id")).longValue();
 }
 long usuario(String nombre,UUID sujeto){long rol=jdbc.queryForObject("insert into rol(nombre_rol,descripcion) values(?,'rollback') returning id_rol",Long.class,nombre+" "+sujeto.toString().substring(0,8));jdbc.update("insert into rol_permiso select ?,id_permiso from permiso where modulo='CRM'",rol);return jdbc.queryForObject("insert into usuario(nombres,correo,estado,id_rol,id_supabase,debe_cambiar_password) values('Rollback',?,'ACTIVO',?,?,false) returning id_usuario",Long.class,sujeto+"@example.test",rol,sujeto);}
 Map<String,Object> datosContrato(String servicio){Map<String,Object>d=new LinkedHashMap<>();d.put("nombre","Servicio contrato");d.put("empresa_id",empresa);d.put("servicio",servicio);d.put("modalidad","PAGADA");d.put("desde",LocalDate.now().minusDays(10).toString());d.put("hasta",LocalDate.now().plusDays(10).toString());d.put("responsable_id",autor);d.put("moneda","PEN");d.put("honorarios",500);d.put("produccion",200);d.put("presupuesto",1000);d.put("alcance","2 videos, 4 publicaciones, 1 informe");d.put("plataformas","FACEBOOK");d.put("frecuencia","Mensual");return d;}
 long contrato(String servicio){long id=((Number)agencia.guardar("contratos",null,datosContrato(servicio),admin).get("id")).longValue();agencia.estado("contratos",id,Map.of("estado","EN_REVISION","version",0),admin);agencia.estado("contratos",id,Map.of("estado","APROBADO","version",1),admin);agencia.estado("contratos",id,Map.of("estado","ACTIVO","version",2),admin);agencia.vincular(id,campana,admin);return id;}
 Map<String,Object> datosAnuncio(long contrato){return new LinkedHashMap<>(Map.of("nombre","Video campaña","contrato_id",contrato,"cuenta_id",cuenta,"campana_id",campana,"codigo","AD-"+UUID.randomUUID(),"formato","VIDEO","fecha",LocalDate.now().toString()));}
 @Test void contratoSeparaPreciosYControlaVersionYPermisos(){
  var c=agencia.guardar("contratos",null,datosContrato("INTEGRAL"),admin);long id=((Number)c.get("id")).longValue();assertEquals(new BigDecimal("500.00"),c.get("honorarios"));
  assertThrows(ExcepcionApi.class,()->agencia.guardar("contratos",null,datosContrato("INTEGRAL"),analista));
  assertThrows(ExcepcionApi.class,()->agencia.estado("contratos",id,Map.of("estado","ACTIVO","version",0),admin));
  agencia.estado("contratos",id,Map.of("estado","EN_REVISION","version",0),admin);
  assertEquals(409,assertThrows(ExcepcionApi.class,()->agencia.estado("contratos",id,Map.of("estado","APROBADO","version",0),admin)).getEstadoHttp().value());
  assertNotNull(agencia.opciones(admin).get("contenidos"));assertNotNull(agencia.informe(id,analista));
 }
 @Test void soloAnalisisNoPublicaNiEditaYNoCruzaEmpresas(){
  long c=contrato("ANALISIS");var d=datosAnuncio(c);var a=agencia.guardar("anuncios",null,d,admin);long id=((Number)a.get("id")).longValue();assertEquals("EXTERNO_SIMULADO",a.get("estado"));
  assertThrows(ExcepcionApi.class,()->agencia.estado("anuncios",id,Map.of("estado","EN_REVISION","version",0),admin));d.put("version",0);assertThrows(ExcepcionApi.class,()->agencia.guardar("anuncios",id,d,admin));
  long otra=jdbc.queryForObject("insert into cliente(nombre,estado) values('Otra empresa','ACTIVO') returning id_cliente",Long.class);jdbc.update("update campana set id_cliente=? where id_campana=?",otra,campana);
  assertThrows(ExcepcionApi.class,()->agencia.guardar("anuncios",null,datosAnuncio(c),admin));
 }
 @Test void publicacionExigeContenidoAprobadoYElHistorialConservaCambios(){
  long c=contrato("DIFUSION");long contenido=jdbc.queryForObject("insert into crm_contenido(nombre,campana_id,responsable_id,estado,texto) values('Video',?,?,'APROBADO','Texto revisado') returning id",Long.class,campana,autor);
  var d=datosAnuncio(c);d.put("contenido_id",contenido);long id=((Number)agencia.guardar("anuncios",null,d,admin).get("id")).longValue();
  int v=0;for(String e:List.of("EN_REVISION","APROBADO","PROGRAMADO","PUBLICADO_SIMULADO"))agencia.estado("anuncios",id,Map.of("estado",e,"version",v++),admin);
  assertEquals("PUBLICADO_SIMULADO",jdbc.queryForObject("select estado from crm_anuncio where id=?",String.class,id));
  assertTrue(((List<?>)agencia.informe(c,admin).get("historial")).size()>=4);
 }
 @Test void metricasPorAnuncioPersistenYNoSeCuentanComoVentasNiDuplicanPauta(){
  long c=contrato("ANALISIS");long id=((Number)agencia.guardar("anuncios",null,datosAnuncio(c),admin).get("id")).longValue();LocalDate hoy=LocalDate.now();
  when(mockaroo.generar(anyList())).thenReturn(CrmMockarooServicio.PAISES.stream().map(p->new CrmMockarooServicio.Fila(hoy,p.codigo(),1000,100,5,new BigDecimal("10"),new BigDecimal("100"))).toList());
  assertEquals(9,agencia.sincronizar(id,hoy,hoy,admin).get("filasNuevas"));assertEquals(0,agencia.sincronizar(id,hoy,hoy,admin).get("filasNuevas"));verify(mockaroo,times(1)).generar(anyList());
  assertEquals(9,((List<?>)agencia.metricas(id,hoy,hoy,analista).get("filas")).size());
  assertEquals(new BigDecimal("90.00"),agencia.informe(c,admin).get("gasto"));assertEquals(0,jdbc.queryForObject("select count(*) from crm_publicidad_demo where campana_id=?",Integer.class,campana));
 }
 @Test void escenariosRelacionadosNoSeDuplicanNiLlamanProveedores(){
  assertEquals(3,agencia.escenarios(admin).get("escenariosCreados"));assertEquals(0,agencia.escenarios(admin).get("escenariosCreados"));verify(mockaroo,never()).generar(anyList());
  assertEquals(3,jdbc.queryForObject("select count(*) from crm_anuncio where codigo like 'ESCENARIO-AD-DEMO-%'",Integer.class));
 }
 @Test void ajusteNecesitaAprobacionYNoEsGasto(){
  long c=contrato("INTEGRAL");long ad=((Number)agencia.guardar("anuncios",null,datosAnuncio(c),admin).get("id")).longValue();
  var d=Map.<String,Object>of("contrato_id",c,"anuncio_id",ad,"nombre","Ampliar pauta","descripcion","Justificación del ajuste","fecha",LocalDate.now().toString(),"cantidad",1,"importe",200);
  long id=((Number)agencia.guardar("ajustes",null,d,admin).get("id")).longValue();assertThrows(ExcepcionApi.class,()->agencia.estado("ajustes",id,Map.of("estado","COMPLETADO","version",0),admin));
  agencia.estado("ajustes",id,Map.of("estado","APROBADO","version",0),admin);agencia.estado("ajustes",id,Map.of("estado","COMPLETADO","version",1),admin);
  assertEquals(new BigDecimal("1200.00"),jdbc.queryForObject("select presupuesto from crm_acuerdo where id=?",BigDecimal.class,c));assertEquals(new BigDecimal("0"),agencia.informe(c,admin).get("gasto"));
 }
}
