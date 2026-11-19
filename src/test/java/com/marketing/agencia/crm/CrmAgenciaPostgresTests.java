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
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CRM_PRUEBAS_BD",matches="true")
class CrmAgenciaPostgresTests {
 @Autowired JdbcTemplate jdbc; @Autowired CrmAgenciaServicio agencia; @Autowired CrmOperacionServicio operaciones;
 @Autowired CrmEntregaServicio entregas; @Autowired CrmAlcanceServicio alcance;
 @Autowired CrmContratoServicio contratacion;
 @Autowired CrmEstadisticasIniciales iniciales;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @MockitoBean JwtDecoder jwt; @MockitoBean CrmMockarooServicio mockaroo;
 UUID admin,analista;long autor,empresa,campana,cuenta;
 @BeforeEach void preparar(){
  when(jwt.decode(anyString())).thenAnswer(inv->org.springframework.security.oauth2.jwt.Jwt.withTokenValue(inv.getArgument(0)).subject(inv.getArgument(0)).header("alg","ES256").build());
  admin=UUID.randomUUID();analista=UUID.randomUUID();autor=usuario("Administrador",admin);usuario("Analista",analista);
  long cliente=jdbc.queryForObject("insert into cliente(nombre,estado) values('Empresa contratos rollback','ACTIVO') returning id_cliente",Long.class);
  empresa=jdbc.queryForObject("select id from crm_cuenta where cliente_id=?",Long.class,cliente);
  long canal=jdbc.queryForObject("select id_canal from canal_difusion where estado='ACTIVO' order by id_canal limit 1",Long.class);
  campana=jdbc.queryForObject("insert into campana(nombre,id_cliente,id_canal,objetivo,presupuesto_asignado,fecha_inicio,fecha_fin,estado,id_usuario_creador) values('Campaña contratos',?,?,'Objetivo',1000,current_date,current_date,'PLANIFICADA',?) returning id_campana",Long.class,cliente,canal,autor);
  cuenta=((Number)agencia.guardar("cuentas-publicitarias",null,Map.of("nombre","Cuenta de prueba","empresa_id",empresa,"codigo","ACC-"+UUID.randomUUID(),"plataforma","FACEBOOK","moneda","PEN"),admin).get("id")).longValue();
 }
 long usuario(String nombre,UUID sujeto){long rol=jdbc.queryForObject("insert into rol(nombre_rol,descripcion) values(?,'rollback') returning id_rol",Long.class,nombre+" "+sujeto.toString().substring(0,8));jdbc.update("insert into rol_permiso select ?,id_permiso from permiso where modulo='CRM'",rol);return jdbc.queryForObject("insert into usuario(nombres,correo,estado,id_rol,id_supabase,debe_cambiar_password) values('Rollback',?,'ACTIVO',?,?,false) returning id_usuario",Long.class,sujeto+"@example.test",rol,sujeto);}
 Map<String,Object> datosContrato(String servicio){Map<String,Object>d=new LinkedHashMap<>();d.put("nombre","Servicio contrato");d.put("empresa_id",empresa);d.put("servicio",servicio);d.put("modalidad","PAGADA");d.put("desde",LocalDate.now().minusDays(10).toString());d.put("hasta",LocalDate.now().plusDays(10).toString());d.put("responsable_id",autor);d.put("moneda","PEN");d.put("honorarios",500);d.put("produccion",200);d.put("presupuesto",1000);d.put("alcance","2 videos, 4 publicaciones, 1 informe");d.put("plataformas","FACEBOOK");d.put("frecuencia","Mensual");return d;}
 long contrato(String servicio){long id=((Number)agencia.guardar("contratos",null,datosContrato(servicio),admin).get("id")).longValue();agencia.estado("contratos",id,Map.of("estado","EN_REVISION","version",0),admin);agencia.estado("contratos",id,Map.of("estado","APROBADO","version",1),admin);agencia.estado("contratos",id,Map.of("estado","ACTIVO","version",2),admin);agencia.vincular(id,campana,admin);return id;}
 Map<String,Object> datosAnuncio(long contrato){return new LinkedHashMap<>(Map.of("nombre","Video campaña","contrato_id",contrato,"cuenta_id",cuenta,"campana_id",campana,"codigo","AD-"+UUID.randomUUID(),"formato","VIDEO","fecha",LocalDate.now().toString()));}
 Map<String,Object> altaBase(String servicio){var d=datosContrato(servicio);d.put("objetivo","Servicio contextual");d.put("modalidad","ORGANICA");d.put("presupuesto",0);d.put("produccion",0);d.put("analisis_desde",LocalDate.now().minusDays(1).toString());d.put("analisis_hasta",LocalDate.now().toString());d.put("prestaciones",List.of(Map.of("prestacion","PUBLICACION","cantidad",2),Map.of("prestacion","ANALISIS","cantidad",2),Map.of("prestacion","INFORMES","cantidad",1)));return d;}
 Map<String,Object> planCanal(String plataforma){return Map.of("plataforma",plataforma,"objetivo","Reconocimiento","programado_en",OffsetDateTime.now().minusHours(1).toString(),"responsable_id",autor,"modalidad","ORGANICA","presupuesto",0);}
 void datosInicialesMockaroo(){
  doAnswer(inv->{
   Map<Long,List<LocalDate>> solicitud=inv.getArgument(0);Map<Long,List<CrmMockarooServicio.Fila>> datos=new LinkedHashMap<>();
   solicitud.forEach((ad,dias)->{List<CrmMockarooServicio.Fila> filas=new ArrayList<>();for(var dia:dias)for(var pais:CrmMockarooServicio.PAISES)filas.add(new CrmMockarooServicio.Fila(dia,pais.codigo(),2000,60,3,new BigDecimal("30.00"),new BigDecimal("60.00")));datos.put(ad,filas);});return datos;
  }).when(mockaroo).generarAnuncios(anyMap());
 }
 UUID borradorDifusionInicial()throws Exception{
  var d=altaBase("DIFUSION");d.put("prestaciones",List.of(Map.of("prestacion","PUBLICACION","cantidad",1)));d.put("piezas",List.of(Map.of("titulo","Anuncio inicial","descripcion","Texto del cliente","formato","TEXTO","fuente","CLIENTE","canales",List.of(planCanal("FACEBOOK")))));
  UUID alta=UUID.randomUUID();contratacion.guardarAlta(alta,Map.of("datos",d,"version",0),admin);return alta;
 }
 @Test void confirmarPorApiCargaEstadisticasAntesDeResponderSinPublicarNiDuplicar()throws Exception{
  datosInicialesMockaroo();UUID alta=borradorDifusionInicial();
  String respuesta=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/crm/contratacion/borradores/"+alta+"/confirmar").header("Authorization","Bearer "+admin).contentType("application/json").content("{\"version\":1}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.datos.estadisticasIniciales.estado").value("LISTO")).andReturn().getResponse().getContentAsString();
  long contrato=new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuesta).path("datos").path("id").asLong();
  long anuncio=jdbc.queryForObject("select id from crm_anuncio where contrato_id=?",Long.class,contrato);
  assertEquals(63,jdbc.queryForObject("select count(*) from crm_anuncio_metrica where anuncio_id=?",Integer.class,anuncio));
  assertEquals(0,new BigDecimal(jdbc.queryForObject("select sum(inversion)::text from crm_anuncio_metrica where anuncio_id=?",String.class,anuncio)).signum());
  assertEquals("BORRADOR",jdbc.queryForObject("select estado from crm_acuerdo where id=?",String.class,contrato));assertEquals("BORRADOR",jdbc.queryForObject("select estado from crm_anuncio where id=?",String.class,anuncio));
  var estadisticas=agencia.metricas(anuncio,LocalDate.now().minusDays(6),LocalDate.now(),admin);assertTrue(((BigDecimal)((Map<?,?>)estadisticas.get("resumen")).get("impresiones")).signum()>0);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/crm/contratacion/borradores/"+alta+"/confirmar").header("Authorization","Bearer "+admin).contentType("application/json").content("{\"version\":1}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  verify(mockaroo,times(1)).generarAnuncios(anyMap());
 }
 @Test void falloDeMockarooConservaContratoYPermiteReintentarSinPrestacionAnalisis()throws Exception{
  UUID alta=borradorDifusionInicial();when(mockaroo.generarAnuncios(anyMap())).thenThrow(new IllegalStateException("Fallo del proveedor"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/crm/contratacion/borradores/"+alta+"/confirmar").header("Authorization","Bearer "+admin).contentType("application/json").content("{\"version\":1}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.datos.estadisticasIniciales.estado").value("ERROR"));
  long contrato=jdbc.queryForObject("select contrato_id from crm_alta_contrato where id=?",Long.class,alta);assertTrue(contrato>0);
  long anuncio=jdbc.queryForObject("select id from crm_anuncio where contrato_id=?",Long.class,contrato);
  assertEquals(0,jdbc.queryForObject("select count(*) from crm_anuncio_metrica where anuncio_id=?",Integer.class,anuncio));
  datosInicialesMockaroo();mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/crm/contratacion/contratos/"+contrato+"/estadisticas-iniciales").header("Authorization","Bearer "+admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.datos.estado").value("LISTO"));
  assertEquals(63,jdbc.queryForObject("select count(*) from crm_anuncio_metrica where anuncio_id=?",Integer.class,anuncio));
  assertThrows(ExcepcionApi.class,()->iniciales.preparar(contrato,analista));
 }
 @Test void asistenteVinculaAudienciaExistenteYGuardaSuNombreCanonico()throws Exception{
  // Las otras pruebas de contratación conservan su flujo manual de persistencia.
  long audiencia=jdbc.queryForObject("insert into segmento_audiencia(nombre,estado,ubicacion_geografica,id_usuario_creador) values('Jóvenes de Lima','ACTIVO','Lima, Perú',?) returning id_segmento",Long.class,autor);
  var opciones=agencia.opciones(admin);assertTrue(((List<Map<String,Object>>)opciones.get("audiencias")).stream().anyMatch(a->((Number)a.get("idSegmento")).longValue()==audiencia));
  var d=altaBase("DIFUSION");d.put("modalidad","PAGADA");d.put("presupuesto",500);
  var canal=new LinkedHashMap<>(planCanal("FACEBOOK"));canal.put("modalidad","PAGADA");canal.put("presupuesto",300);canal.put("audiencia_id",audiencia);canal.put("audiencia","Nombre enviado incorrecto");canal.put("cobertura","");
  d.put("piezas",List.of(Map.of("titulo","Anuncio de texto","descripcion","Contenido","formato","TEXTO","fuente","CLIENTE","canales",List.of(canal))));
  UUID alta=UUID.randomUUID();contratacion.guardarAlta(alta,Map.of("datos",d,"version",0),admin);long contrato=((Number)contratacion.confirmar(alta,1,admin).get("id")).longValue();
  assertEquals(1,jdbc.queryForObject("select count(*) from campana_audiencia ca join crm_acuerdo_campana c on c.campana_id=ca.id_campana where c.contrato_id=? and ca.id_segmento=?",Integer.class,contrato,audiencia));
  assertEquals("Jóvenes de Lima",jdbc.queryForObject("select datos #>> '{piezas,0,canales,0,audiencia}' from crm_contrato_config where contrato_id=?",String.class,contrato));
  assertEquals("Lima, Perú",jdbc.queryForObject("select datos #>> '{piezas,0,canales,0,cobertura}' from crm_contrato_config where contrato_id=?",String.class,contrato));
  assertEquals(contrato,contratacion.confirmar(alta,1,admin).get("id"));
  assertEquals(1,jdbc.queryForObject("select count(*) from segmento_audiencia where id_segmento=?",Integer.class,audiencia));
 }
 @Test void asistenteDifusionCargaAntesDelContratoYConfirmaSinDuplicados()throws Exception{
  UUID id=UUID.randomUUID(),archivo=UUID.randomUUID();var d=altaBase("DIFUSION");contratacion.guardarAlta(id,Map.of("datos",d,"version",0),admin);byte[]video=new byte[32];System.arraycopy("ftypisom".getBytes(),0,video,4,8);contratacion.subir(id,archivo,new org.springframework.mock.web.MockMultipartFile("archivo","cliente.mp4","video/mp4",video),admin);
  d.put("piezas",List.of(Map.of("clave",UUID.randomUUID().toString(),"titulo","Video del cliente","descripcion","Contenido base","formato","VIDEO","fuente","CLIENTE","pendiente",false,"archivos",List.of(Map.of("id",archivo.toString())),"canales",List.of(planCanal("FACEBOOK"),planCanal("TIKTOK")))));contratacion.guardarAlta(id,Map.of("datos",d,"version",1),admin);
  long c=((Number)contratacion.confirmar(id,2,admin).get("id")).longValue();assertEquals(c,contratacion.confirmar(id,2,admin).get("id"));assertEquals(2,jdbc.queryForObject("select count(*) from crm_anuncio where contrato_id=?",Integer.class,c));assertEquals(1,jdbc.queryForObject("select count(*) from crm_alta_archivo where alta_id=?",Integer.class,id));assertEquals(2,jdbc.queryForObject("select count(*) from crm_media_anuncio m join crm_anuncio a on a.id=m.anuncio_id where a.contrato_id=?",Integer.class,c));assertEquals("BORRADOR",((Map<?,?>)contratacion.detalle(c,admin).get("contrato")).get("estado"));
 }
 @Test void asistenteAnalisisVinculaReferenciaSinDuplicarNiCrearPublicaciones()throws Exception{
  long origen=contrato("ANALISIS"),ad=((Number)agencia.guardar("anuncios",null,datosAnuncio(origen),admin).get("id")).longValue();String codigo=jdbc.queryForObject("select codigo from crm_anuncio where id=?",String.class,ad),cuentaCodigo=jdbc.queryForObject("select codigo from crm_cuenta_publicitaria where id=?",String.class,cuenta);
  UUID id=UUID.randomUUID();var d=altaBase("ANALISIS");d.put("prestaciones",List.of(Map.of("prestacion","ANALISIS","cantidad",2),Map.of("prestacion","INFORMES","cantidad",1)));d.put("referencias",List.of(Map.of("plataforma","FACEBOOK","nombre","Referencia previa","referencia",codigo,"cuenta_codigo",cuentaCodigo,"cuenta_nombre","Cuenta del cliente")));contratacion.guardarAlta(id,Map.of("datos",d,"version",0),admin);long c=((Number)contratacion.confirmar(id,1,admin).get("id")).longValue();assertEquals(0,jdbc.queryForObject("select count(*) from crm_anuncio where contrato_id=?",Integer.class,c));assertEquals(1,jdbc.queryForObject("select count(*) from crm_contrato_anuncio where contrato_id=? and anuncio_id=? and origen='REFERENCIA'",Integer.class,c,ad));assertEquals(1,((List<?>)contratacion.detalle(c,analista).get("anuncios")).size());assertEquals(0,((BigDecimal)((Map<?,?>)contratacion.detalle(c,admin).get("contrato")).get("presupuesto")).signum());
  LocalDate hoy=LocalDate.now();when(mockaroo.generar(anyList())).thenReturn(CrmMockarooServicio.PAISES.stream().map(p->new CrmMockarooServicio.Fila(hoy,p.codigo(),1000,100,5,new BigDecimal("10"),new BigDecimal("100"))).toList());for(String estado:List.of("EN_REVISION","APROBADO","ACTIVO")){int v=jdbc.queryForObject("select version from crm_acuerdo where id=?",Integer.class,c);agencia.estado("contratos",c,Map.of("estado",estado,"version",v),admin);}assertEquals(9,contratacion.sincronizar(c,ad,Map.of("desde",hoy.toString(),"hasta",hoy.toString()),admin).get("filasNuevas"));assertEquals(0,contratacion.sincronizar(c,ad,Map.of("desde",hoy.toString(),"hasta",hoy.toString()),admin).get("filasNuevas"));verify(mockaroo,times(1)).generar(anyList());assertEquals(new BigDecimal("90.00"),agencia.informe(c,admin).get("gasto"));assertEquals(1,((List<?>)entregas.comparar(c,hoy,hoy,List.of(ad),analista).get("resultados")).size());
 }
 @Test void asistenteIntegralAdmiteProduccionPendienteYReferenciaSinDatos()throws Exception{
  UUID id=UUID.randomUUID();var d=altaBase("INTEGRAL");d.put("prestaciones",List.of(Map.of("prestacion","PRODUCCION","cantidad",1),Map.of("prestacion","PUBLICACION","cantidad",1),Map.of("prestacion","ANALISIS","cantidad",2),Map.of("prestacion","INFORMES","cantidad",1)));d.put("piezas",List.of(Map.of("clave",UUID.randomUUID().toString(),"titulo","Video por producir","descripcion","Pendiente legítimo","formato","VIDEO","fuente","AGENCIA","pendiente",true,"entrega",LocalDate.now().toString(),"responsable_id",autor,"canales",List.of(planCanal("FACEBOOK")))));d.put("referencias",List.of(Map.of("plataforma","TIKTOK","nombre","Anuncio existente","referencia","TIKTOK-"+UUID.randomUUID(),"cuenta_codigo","CUENTA-"+UUID.randomUUID(),"cuenta_nombre","Cuenta TikTok")));contratacion.guardarAlta(id,Map.of("datos",d,"version",0),admin);long c=((Number)contratacion.confirmar(id,1,admin).get("id")).longValue();var f=contratacion.detalle(c,admin);assertEquals(2,((List<?>)f.get("anuncios")).size());assertEquals(0,jdbc.queryForObject("select count(*) from crm_media_anuncio m join crm_anuncio a on a.id=m.anuncio_id where a.contrato_id=?",Integer.class,c));assertEquals(0,jdbc.queryForObject("select count(*) from crm_anuncio_metrica m join crm_anuncio a on a.id=m.anuncio_id where a.contrato_id=?",Integer.class,c));
  jdbc.update("update crm_acuerdo set estado=\'ACTIVO\' where id=?",c);jdbc.update("update crm_contenido set estado=\'APROBADO\' where id in(select contenido_id from crm_anuncio where contrato_id=?)",c);long pendiente=jdbc.queryForObject("select id from crm_anuncio where contrato_id=? and estado=\'BORRADOR\'",Long.class,c);agencia.estado("anuncios",pendiente,Map.of("estado","EN_REVISION","version",0),admin);assertThrows(ExcepcionApi.class,()->agencia.estado("anuncios",pendiente,Map.of("estado","APROBADO","version",1),admin));
 }
 @Test void borradorParcialPrivadoVersionadoYAltaIncompletaNoConfirma()throws Exception{
  UUID id=UUID.randomUUID();contratacion.guardarAlta(id,Map.of("datos",Map.of("nombre","Pendiente"),"version",0),admin);assertEquals("Pendiente",((Map<?,?>)contratacion.detalleAlta(id,admin).get("datos")).get("nombre"));assertThrows(ExcepcionApi.class,()->contratacion.detalleAlta(id,analista));assertThrows(ExcepcionApi.class,()->contratacion.guardarAlta(id,Map.of("datos",Map.of("nombre","Cambio"),"version",0),admin));assertThrows(ExcepcionApi.class,()->contratacion.confirmar(id,1,admin));assertNull(jdbc.queryForObject("select contrato_id from crm_alta_contrato where id=?",Long.class,id));
 }
 @Test void ampliarContratoConservaIdentidadAlcanceYElementos()throws Exception{
  UUID id=UUID.randomUUID();var d=altaBase("DIFUSION");d.put("piezas",List.of(Map.of("clave","original","titulo","Texto original","descripcion","Texto del cliente","formato","TEXTO","fuente","CLIENTE","canales",List.of(planCanal("FACEBOOK")))));contratacion.guardarAlta(id,Map.of("datos",d,"version",0),admin);long c=((Number)contratacion.confirmar(id,1,admin).get("id")).longValue();
  int trabajos=jdbc.queryForObject("select count(*) from crm_trabajo_servicio where contrato_id=?",Integer.class,c);UUID ampliacion=(UUID)contratacion.prepararAmpliacion(c,admin).get("id");var alta=contratacion.detalleAlta(ampliacion,admin);Map<String,Object> datos=new LinkedHashMap<>((Map<String,Object>)alta.get("datos"));datos.put("piezas",List.of(Map.of("clave","adicional","titulo","Texto adicional","descripcion","Segundo texto","formato","TEXTO","fuente","CLIENTE","canales",List.of(planCanal("TIKTOK")))));contratacion.guardarAlta(ampliacion,Map.of("datos",datos,"version",alta.get("version")),admin);
  assertEquals(c,contratacion.confirmar(ampliacion,2,admin).get("id"));assertEquals(c,contratacion.confirmar(ampliacion,2,admin).get("id"));assertEquals(2,jdbc.queryForObject("select count(*) from crm_anuncio where contrato_id=?",Integer.class,c));assertEquals(1,jdbc.queryForObject("select count(*) from crm_acuerdo_campana where contrato_id=?",Integer.class,c));assertEquals(trabajos,jdbc.queryForObject("select count(*) from crm_trabajo_servicio where contrato_id=?",Integer.class,c));assertEquals(2,((List<?>)((Map<?,?>)contratacion.detalle(c,admin).get("configuracion")).get("piezas")).size());
  UUID excedido=(UUID)contratacion.prepararAmpliacion(c,admin).get("id");var extra=contratacion.detalleAlta(excedido,admin);Map<String,Object> mas=new LinkedHashMap<>((Map<String,Object>)extra.get("datos"));mas.put("piezas",datos.get("piezas"));contratacion.guardarAlta(excedido,Map.of("datos",mas,"version",extra.get("version")),admin);assertThrows(ExcepcionApi.class,()->contratacion.confirmar(excedido,2,admin));assertEquals(2,jdbc.queryForObject("select count(*) from crm_anuncio where contrato_id=?",Integer.class,c));
  jdbc.update("update crm_acuerdo set estado='CANCELADO' where id=?",c);assertThrows(ExcepcionApi.class,()->contratacion.prepararAmpliacion(c,admin));
 }
 long clientePortal(UUID sujeto,long empresa){long rol=jdbc.queryForObject("select id_rol from rol where lower(nombre_rol)='cliente' limit 1",Long.class);long id=jdbc.queryForObject("insert into usuario(nombres,correo,estado,id_rol,id_supabase,debe_cambiar_password) values('Cliente prueba',?,'ACTIVO',?,?,false) returning id_usuario",Long.class,sujeto+"@example.test",rol,sujeto);entregas.asignar(Map.of("usuario_id",id,"empresa_id",empresa,"activo",true,"version",-1),admin);return id;}
 @Test void alcanceLimitaAnunciosYEntregables(){
  long c=contrato("INTEGRAL");alcance.guardar(c,"INTEGRAL",List.of(Map.of("prestacion","PUBLICACION","cantidad",1),Map.of("prestacion","INFORMES","cantidad",1)));
  agencia.guardar("anuncios",null,datosAnuncio(c),admin);assertThrows(ExcepcionApi.class,()->agencia.guardar("anuncios",null,datosAnuncio(c),admin));
  var d=Map.<String,Object>of("contrato_id",c,"nombre","Dos informes","descripcion","Alcance excedido","fecha",LocalDate.now().toString(),"cantidad",2,"importe",0,"prestacion","INFORMES");long id=((Number)agencia.guardar("entregables",null,d,admin).get("id")).longValue();agencia.estado("entregables",id,Map.of("estado","APROBADO","version",0),admin);assertThrows(ExcepcionApi.class,()->agencia.estado("entregables",id,Map.of("estado","COMPLETADO","version",1),admin));
 }
 @Test void materialesRevisionClienteYPublicacionRespetanLaUltimaVersion()throws Exception{
  long c=contrato("DIFUSION"),ad=((Number)agencia.guardar("anuncios",null,datosAnuncio(c),admin).get("id")).longValue();UUID cliente=UUID.randomUUID(),ajeno=UUID.randomUUID();clientePortal(cliente,empresa);long otra=jdbc.queryForObject("insert into cliente(nombre,estado) values('Empresa ajena portal','ACTIVO') returning id_cliente",Long.class);long otraCuenta=jdbc.queryForObject("select id from crm_cuenta where cliente_id=?",Long.class,otra);clientePortal(ajeno,otraCuenta);
  byte[] video=new byte[32];System.arraycopy("ftypisom".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,video,4,8);
  var archivo=new org.springframework.mock.web.MockMultipartFile("archivo","video.mp4","video/mp4",video);long medio=((Number)entregas.subir(ad,archivo,admin).get("id")).longValue();assertArrayEquals(video,(byte[])entregas.archivo(medio,admin,false).get("contenido"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/crm/entregas/medios/"+medio).header("Authorization","Bearer "+admin).header("Range","bytes=0-7")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isPartialContent());
  assertThrows(ExcepcionApi.class,()->entregas.subir(ad,new org.springframework.mock.web.MockMultipartFile("archivo","x.mp4","video/mp4","<script>malicioso</script>".getBytes()),admin));
  entregas.solicitarRevision(ad,admin);long revision=jdbc.queryForObject("select id from crm_revision_cliente where anuncio_id=? and estado='PENDIENTE'",Long.class,ad);assertThrows(ExcepcionApi.class,()->entregas.archivo(medio,ajeno,true));assertThrows(ExcepcionApi.class,()->entregas.portalContrato(c,ajeno));
  entregas.responder(revision,Map.of("estado","APROBADO","version",0),cliente);assertNotNull(entregas.portalContrato(c,cliente));entregas.subir(ad,archivo,admin);assertEquals("ANULADA",jdbc.queryForObject("select estado from crm_revision_cliente where id=?",String.class,revision));assertThrows(ExcepcionApi.class,()->entregas.responder(revision,Map.of("estado","APROBADO","version",1),cliente));
  long contenido=jdbc.queryForObject("insert into crm_contenido(nombre,campana_id,responsable_id,estado,texto) values('Video aprobado',?,?,'APROBADO','Texto') returning id",Long.class,campana,autor);jdbc.update("update crm_anuncio set contenido_id=? where id=?",contenido,ad);int v=0;for(String e:List.of("EN_REVISION","APROBADO","PROGRAMADO"))agencia.estado("anuncios",ad,Map.of("estado",e,"version",v++),admin);assertThrows(ExcepcionApi.class,()->agencia.estado("anuncios",ad,Map.of("estado","PUBLICADO_SIMULADO","version",3),admin));
  entregas.solicitarRevision(ad,admin);long nueva=jdbc.queryForObject("select id from crm_revision_cliente where anuncio_id=? and estado='PENDIENTE'",Long.class,ad);entregas.responder(nueva,Map.of("estado","APROBADO","version",0),cliente);agencia.estado("anuncios",ad,Map.of("estado","PUBLICADO_SIMULADO","version",3),admin);
 }
 @Test void informesPdfInmutablesPublicacionYRevocacionPortal()throws Exception{
  long c=contrato("INTEGRAL");UUID cliente=UUID.randomUUID(),otro=UUID.randomUUID();long usuario=clientePortal(cliente,empresa);long otra=jdbc.queryForObject("insert into cliente(nombre,estado) values('Otra empresa PDF','ACTIVO') returning id_cliente",Long.class);clientePortal(otro,jdbc.queryForObject("select id from crm_cuenta where cliente_id=?",Long.class,otra));
  Map<String,Object>d=Map.of("titulo","Informe integral Café Mantaro","conclusiones","La campaña mantiene un buen rendimiento. Próxima acción: revisar el CPC.","idempotencia",UUID.randomUUID().toString());long id=((Number)entregas.generar(c,d,admin).get("id")).longValue();assertEquals(id,entregas.generar(c,d,admin).get("id"));byte[] bytes=entregas.descargarPdf(id,admin,false);assertThrows(ExcepcionApi.class,()->entregas.descargarPdf(id,cliente,true));entregas.publicar(id,admin);assertArrayEquals(bytes,entregas.descargarPdf(id,cliente,true));assertThrows(ExcepcionApi.class,()->entregas.descargarPdf(id,otro,true));
  try(var doc=org.apache.pdfbox.Loader.loadPDF(bytes)){String texto=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);assertTrue(texto.contains("Café Mantaro"));assertTrue(texto.contains("CONCLUSIONES"));var imagen=new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,100);java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/evidencia"));javax.imageio.ImageIO.write(imagen,"png",java.nio.file.Path.of("target/evidencia/informe-pdf.png").toFile());}
  jdbc.update("update crm_acuerdo set presupuesto=2000 where id=?",c);assertArrayEquals(bytes,entregas.descargarPdf(id,cliente,true));entregas.asignar(Map.of("usuario_id",usuario,"empresa_id",empresa,"activo",false,"version",0),admin);assertThrows(ExcepcionApi.class,()->entregas.portal(cliente));assertThrows(ExcepcionApi.class,()->agencia.opciones(cliente));
 }
 @Test void comparacionUsaPeriodoAnteriorYNoCruzaContratos(){
  long c=contrato("ANALISIS"),ad=((Number)agencia.guardar("anuncios",null,datosAnuncio(c),admin).get("id")).longValue();LocalDate hoy=LocalDate.now();jdbc.update("insert into crm_anuncio_metrica values(?,?,'PE',1000,100,10,10,100),(?,?,'PE',2000,300,20,30,200)",ad,hoy.minusDays(1),ad,hoy);
  var comparacion=entregas.comparar(c,hoy,hoy,List.of(ad),analista);var fila=(Map<?,?>)((List<?>)comparacion.get("resultados")).getFirst();assertEquals(new BigDecimal("200.00"),((Map<?,?>)fila.get("variacion")).get("clics"));long otro=((Number)agencia.guardar("contratos",null,datosContrato("ANALISIS"),admin).get("id")).longValue();assertThrows(ExcepcionApi.class,()->entregas.comparar(otro,hoy,hoy,List.of(ad),admin));
 }
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
  var informe=agencia.informe(c,analista);assertEquals(true,informe.get("tieneMetricas"));
  var resumen=(Map<?,?>)informe.get("resumen");assertEquals(0,new BigDecimal("9000").compareTo((BigDecimal)resumen.get("impresiones")));assertEquals(0,new BigDecimal("900").compareTo((BigDecimal)resumen.get("clics")));assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)resumen.get("ctr")));assertEquals(1,((List<?>)informe.get("dias")).size());
  // Otro contrato de la misma empresa no hereda estadísticas ni redacta informes por consultar.
  var otro=agencia.guardar("contratos",null,datosContrato("ANALISIS"),admin);var vacio=agencia.informe(((Number)otro.get("id")).longValue(),analista);assertEquals(false,vacio.get("tieneMetricas"));assertEquals(0,jdbc.queryForObject("select count(*) from crm_trabajo_servicio where tipo='INFORME' and contrato_id=?",Integer.class,c));
 }
 @Test void escenariosRelacionadosNoSeDuplicanNiLlamanProveedores(){
  assertFalse(agencia.organico(((Number)agencia.guardar("anuncios",null,datosAnuncio(contrato("ANALISIS")),admin).get("id")).longValue()));
  int existentes=jdbc.queryForObject("select count(*) from crm_anuncio where codigo in ('ESCENARIO-AD-DEMO-1','ESCENARIO-AD-DEMO-2','ESCENARIO-AD-DEMO-3')",Integer.class);
  assertEquals(3-existentes,agencia.escenarios(admin).get("escenariosCreados"));assertEquals(0,agencia.escenarios(admin).get("escenariosCreados"));verify(mockaroo,never()).generar(anyList());
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
