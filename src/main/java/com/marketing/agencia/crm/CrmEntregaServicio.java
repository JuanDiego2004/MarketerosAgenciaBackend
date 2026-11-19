package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmEntregaServicio {
 private final JdbcTemplate jdbc;private final CrmAcceso acceso;private final CrmAgenciaServicio agencia;private final CrmAlcanceServicio alcance;private final CrmOperacionServicio auditoria;private final CrmInformePdf pdf;private final ObjectMapper json;
 private static final String ANUNCIOS_PORTAL = """
  from public.crm_acuerdo visible
  join public.crm_cuenta empresa on empresa.id=visible.empresa_id
  join public.crm_anuncio a on (a.contrato_id=visible.id or exists(
    select 1 from public.crm_contrato_anuncio v where v.anuncio_id=a.id and v.contrato_id=visible.id))
  join public.crm_acuerdo origen on origen.id=a.contrato_id and origen.empresa_id=visible.empresa_id
  join public.crm_cuenta_publicitaria cuenta on cuenta.id=a.cuenta_id and cuenta.empresa_id=visible.empresa_id
  join public.campana ca on ca.id_campana=a.campana_id and ca.id_cliente=empresa.cliente_id
  """;
 private long leer(UUID u){return acceso.exigir(u,"atribucion",false);}private long gestionar(UUID u){return acceso.exigir(u,"cuentas",true);}
 private Map<String,Object> contrato(long id){var filas=jdbc.queryForList("select * from public.crm_acuerdo where id=? and tipo='CONTRATO'",id);if(filas.isEmpty())throw noExiste();return filas.getFirst();}
 private ExcepcionApi noExiste(){return new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_ENTREGA_NO_EXISTE","Registro no disponible");}
 private Map<String,Object> anuncio(long id){var f=jdbc.queryForList("select a.*,c.empresa_id,c.servicio,c.estado contrato_estado,p.plataforma from public.crm_anuncio a join public.crm_acuerdo c on c.id=a.contrato_id join public.crm_cuenta_publicitaria p on p.id=a.cuenta_id where a.id=?",id);if(f.isEmpty())throw noExiste();return f.getFirst();}
 private void operativo(Map<String,Object>a){if(!Set.of("ACTIVO","APROBADO").contains(a.get("contrato_estado")))throw invalido("El contrato debe estar operativo");if("ANALISIS".equals(a.get("servicio")))throw invalido("Solo análisis no permite modificar contenido");}
 public List<Map<String,Object>> medios(long ad,UUID u){leer(u);anuncio(ad);return listaMedios(ad);}
 private List<Map<String,Object>> listaMedios(long ad){return jdbc.queryForList("select id,nombre,mime,octet_length(contenido) bytes,created_at from public.crm_media_anuncio where anuncio_id=? order by id desc limit 30",ad);}
 @Transactional public Map<String,Object> subir(long ad,MultipartFile archivo,UUID u)throws IOException{
  long autor=gestionar(u);var a=anuncio(ad);operativo(a);if(!Set.of("BORRADOR","EN_REVISION","APROBADO").contains(a.get("estado")))throw invalido("Crea otro anuncio para cambiar un material ya programado o publicado");
  long c=((Number)a.get("contrato_id")).longValue();if(alcance.limite(c,"PRODUCCION")+alcance.limite(c,"EDICION")+alcance.limite(c,"PUBLICACION")==0)throw invalido("El contrato no incluye gestión de materiales");
  if(archivo.isEmpty()||archivo.getSize()>20971520)throw invalido("El material debe pesar entre 1 byte y 20 MB");byte[] bytes=archivo.getBytes();String mime=tipo(bytes);
  if((mime.startsWith("image/")&&!"IMAGEN".equals(a.get("formato")))||(mime.startsWith("video/")&&!"VIDEO".equals(a.get("formato"))))throw invalido("El tipo de material debe coincidir con el formato del anuncio");
  String nombre=Objects.toString(archivo.getOriginalFilename(),"material").replaceAll("[\\\\/\\p{Cntrl}]","_");if(nombre.length()>160)nombre=nombre.substring(nombre.length()-160);
  jdbc.queryForList("select id from public.crm_anuncio where id=? for update",ad);
  long id=jdbc.queryForObject("insert into public.crm_media_anuncio(anuncio_id,nombre,mime,contenido,autor_id) values(?,?,?,?,?) returning id",Long.class,ad,nombre,mime,bytes,autor);
  jdbc.update("update public.crm_revision_cliente set estado='ANULADA',version=version+1 where anuncio_id=? and estado<>'ANULADA'",ad);auditoria.auditar("agencia_anuncios",ad,"SUBIR_MATERIAL_"+id,autor);return Map.of("id",id);
 }
 static String tipo(byte[] b){
  if(b.length>8&&b[0]==(byte)137&&b[1]==80&&b[2]==78&&b[3]==71)return imagen(b,"image/png");
  if(b.length>3&&b[0]==(byte)255&&b[1]==(byte)216&&b[2]==(byte)255)return imagen(b,"image/jpeg");
  if(b.length>24&&new String(b,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp"))return "video/mp4";
  if(b.length>16&&b[0]==0x1a&&b[1]==0x45&&b[2]==(byte)0xdf&&b[3]==(byte)0xa3)return "video/webm";
  throw invalido("Formato inválido. Selecciona PNG, JPEG, MP4 o WebM");
 }
 private static String imagen(byte[] b,String mime){try(var in=javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(b))){var r=javax.imageio.ImageIO.getImageReaders(in);if(!r.hasNext())throw new IOException();var lector=r.next();try{lector.setInput(in);if((long)lector.getWidth(0)*lector.getHeight(0)>20000000)throw new IOException();}finally{lector.dispose();}return mime;}catch(IOException e){throw invalido("La imagen está dañada o supera 20 megapíxeles");}}
 public Map<String,Object> archivo(long id,UUID u,boolean portal) {
  var filas=jdbc.queryForList("""
   select m.*,c.empresa_id,c.servicio,c.estado contrato_estado,a.contrato_id
   from public.crm_media_anuncio m join public.crm_anuncio a on a.id=m.anuncio_id
   join public.crm_acuerdo c on c.id=a.contrato_id where m.id=?
   """,id);
  if(filas.isEmpty())throw noExiste();
  var medio=filas.getFirst();
  if(portal) {
   if(empresaPortal(u)!=((Number)medio.get("empresa_id")).longValue())throw noExiste();
   if("ANALISIS".equals(medio.get("servicio"))||Set.of("BORRADOR","EN_REVISION").contains(medio.get("contrato_estado")))throw noExiste();
   alcance.exigir(((Number)medio.get("contrato_id")).longValue(),"PUBLICACION");
   if(jdbc.queryForObject("select count(*) from public.crm_revision_cliente where media_id=? and estado<>'ANULADA'",Integer.class,id)==0)throw noExiste();
  } else leer(u);
  return medio;
 }
 public Map<String,Object> comparar(long contrato,LocalDate desde,LocalDate hasta,List<Long> anuncios,UUID u){
  leer(u);contrato(contrato);CrmPublicidadDemoServicio.periodo(desde,hasta);if(anuncios.isEmpty()||anuncios.size()>10||new HashSet<>(anuncios).size()!=anuncios.size())throw invalido("Selecciona de 1 a 10 anuncios distintos");
  long dias=java.time.temporal.ChronoUnit.DAYS.between(desde,hasta)+1;LocalDate anterior=desde.minusDays(dias);List<Map<String,Object>> resultados=new ArrayList<>();
  for(long id:anuncios){var a=anuncio(id);if(((Number)a.get("contrato_id")).longValue()!=contrato && !jdbc.queryForObject("select exists(select 1 from public.crm_contrato_anuncio where contrato_id=? and anuncio_id=?)",Boolean.class,contrato,id))throw invalido("Selecciona anuncios del mismo contrato y moneda");Map<String,Object> actual=resumen(id,desde,hasta),previo=resumen(id,anterior,desde.minusDays(1)),variacion=new LinkedHashMap<>();for(String k:List.of("impresiones","clics","conversiones","inversion","ctr","cpc","cpm")){BigDecimal p=(BigDecimal)previo.get(k),v=(BigDecimal)actual.get(k);variacion.put(k,p==null||p.signum()==0||v==null?null:v.subtract(p).multiply(BigDecimal.valueOf(100)).divide(p,2,RoundingMode.HALF_UP));}resultados.add(Map.of("anuncio",a,"actual",actual,"anterior",previo,"variacion",variacion));}
  Set<String> plataformas=new TreeSet<>();for(var r:resultados)plataformas.add(((Map<?,?>)r.get("anuncio")).get("plataforma").toString());List<Map<String,Object>> grupos=new ArrayList<>();
  for(String plataforma:plataformas){List<Map<String,Object>> actual=new ArrayList<>(),previo=new ArrayList<>();for(var r:resultados)if(plataforma.equals(((Map<?,?>)r.get("anuncio")).get("plataforma"))){actual.add(copiaMapa(r.get("actual")));previo.add(copiaMapa(r.get("anterior")));}grupos.add(Map.of("plataforma",plataforma,"actual",CrmPublicidadDemoServicio.resumen(actual),"anterior",CrmPublicidadDemoServicio.resumen(previo)));}
  return Map.of("resultados",resultados,"plataformas",grupos,"desde",desde,"hasta",hasta,"anteriorDesde",anterior,"anteriorHasta",desde.minusDays(1),"moneda",contrato(contrato).get("moneda"));
 }
 private Map<String,Object> copiaMapa(Object o){Map<String,Object> m=new LinkedHashMap<>();((Map<?,?>)o).forEach((k,v)->m.put(k.toString(),v));return m;}
 private Map<String,Object> resumen(long id,LocalDate desde,LocalDate hasta){return CrmPublicidadDemoServicio.resumen(jdbc.queryForList("select impresiones,clics,conversiones,inversion,ingresos from public.crm_anuncio_metrica where anuncio_id=? and fecha between ? and ?",id,desde,hasta));}
 public List<Map<String,Object>> versiones(long c,UUID u,boolean portal){if(portal)exigirContratoPortal(c,u);else leer(u);contrato(c);return jdbc.queryForList("select id,numero,titulo,publicado,created_at from public.crm_informe_version where contrato_id=?"+(portal?" and publicado":"")+" order by numero desc limit 100",c);}
 @Transactional public Map<String,Object> generar(long id,Map<String,Object>d,UUID u)throws IOException{
  long autor=acceso.exigir(u,"contenido",true);alcance.exigir(id,"INFORMES");var c=contrato(id);String titulo=texto(d,"titulo",160,true),conclusiones=texto(d,"conclusiones",10000,true);UUID clave;try{clave=UUID.fromString(Objects.toString(d.get("idempotencia"),""));}catch(Exception e){throw invalido("Reabre el formulario del informe");}
  jdbc.queryForList("select id from public.crm_acuerdo where id=? for update",id);var anteriores=jdbc.queryForList("select id,contrato_id,autor_id,titulo,conclusiones from public.crm_informe_version where idempotencia=?",clave);if(!anteriores.isEmpty()){var a=anteriores.getFirst();if(!a.get("contrato_id").equals(id)||!a.get("autor_id").equals(autor)||!a.get("titulo").equals(titulo)||!a.get("conclusiones").equals(conclusiones))throw invalido("La solicitud ya se utilizó con otros datos");return Map.of("id",a.get("id"));}
  int numero=jdbc.queryForObject("select coalesce(max(numero),0)+1 from public.crm_informe_version where contrato_id=?",Integer.class,id);Map<String,Object> snapshot=agencia.informe(id,u);snapshot.remove("historial");snapshot.put("trabajos",((List<?>)snapshot.get("trabajos")).stream().filter(t->Set.of("APROBADO","COMPLETADO").contains(((Map<?,?>)t).get("estado"))).toList());byte[] documento=pdf.generar(snapshot,titulo,conclusiones,numero);
  long version=jdbc.queryForObject("insert into public.crm_informe_version(contrato_id,numero,titulo,conclusiones,datos,pdf,autor_id,idempotencia) values(?,?,?,?,?::jsonb,?,?,?) returning id",Long.class,id,numero,titulo,conclusiones,json.writeValueAsString(snapshot),documento,autor,clave);auditoria.auditar("agencia_contratos",id,"GENERAR_PDF_V"+numero,autor);return Map.of("id",version,"numero",numero);
 }
 @Transactional public void publicar(long id,UUID u){long autor=gestionar(u);if(jdbc.update("update public.crm_informe_version set publicado=true where id=? and not publicado",id)>0)auditoria.auditar("agencia_informes",id,"PUBLICAR_CLIENTE",autor);}
 public byte[] descargarPdf(long id,UUID u,boolean portal){var f=jdbc.queryForList("select contrato_id,pdf,publicado from public.crm_informe_version where id=?",id);if(f.isEmpty())throw noExiste();var v=f.getFirst();if(portal){exigirContratoPortal(((Number)v.get("contrato_id")).longValue(),u);if(!Boolean.TRUE.equals(v.get("publicado")))throw noExiste();}else leer(u);return (byte[])v.get("pdf");}
 public Map<String,Object> accesos(UUID u){acceso.exigir(u,"campos",true);return Map.of("filas",jdbc.queryForList("select p.*,u.nombres,c.nombre empresa from public.crm_portal_acceso p join public.usuario u on u.id_usuario=p.usuario_id join public.crm_cuenta c on c.id=p.empresa_id order by p.usuario_id limit 1000"),"usuarios",jdbc.queryForList("select u.id_usuario id,u.nombres nombre from public.usuario u join public.rol r using(id_rol) where lower(r.nombre_rol)='cliente' and u.estado='ACTIVO' order by u.nombres limit 1000"),"empresas",jdbc.queryForList("select id,nombre from public.crm_cuenta where fusionada_en is null order by nombre limit 1000"));}
 @Transactional public void asignar(Map<String,Object>d,UUID u){long autor=acceso.exigir(u,"campos",true),usuario=numero(d,"usuario_id"),empresa=numero(d,"empresa_id");if(jdbc.queryForObject("select count(*) from public.usuario u join public.rol r using(id_rol) where u.id_usuario=? and lower(r.nombre_rol)='cliente' and u.estado='ACTIVO'",Integer.class,usuario)!=1)throw invalido("Selecciona un usuario activo con rol Cliente");if(jdbc.queryForObject("select count(*) from public.crm_cuenta where id=? and fusionada_en is null",Integer.class,empresa)!=1)throw invalido("Empresa inexistente");if(!(d.get("activo") instanceof Boolean))throw invalido("Indica el estado del acceso");int version=entero(d,"version",-1,Integer.MAX_VALUE);if(version<0){jdbc.update("insert into public.crm_portal_acceso(usuario_id,empresa_id,activo,otorgado_por) values(?,?,?,?)",usuario,empresa,d.get("activo"),autor);}else if(jdbc.update("update public.crm_portal_acceso set empresa_id=?,activo=?,version=version+1,otorgado_por=?,updated_at=now() where usuario_id=? and version=?",empresa,d.get("activo"),autor,usuario,version)==0)throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","El acceso cambió; actualiza antes de guardar");auditoria.auditar("agencia_portal",usuario,"ASIGNAR_EMPRESA",autor);}
 private long empresaPortal(UUID u){var filas=jdbc.queryForList("select p.empresa_id from public.crm_portal_acceso p join public.usuario u on u.id_usuario=p.usuario_id join public.rol r using(id_rol) where u.id_supabase=? and u.estado='ACTIVO' and lower(r.nombre_rol)='cliente' and p.activo",u);if(filas.isEmpty())throw new ExcepcionApi(HttpStatus.FORBIDDEN,"CRM_PORTAL_DENEGADO","Tu usuario no tiene un acceso activo al portal de una empresa");return ((Number)filas.getFirst().get("empresa_id")).longValue();}
 private void exigirContratoPortal(long id,UUID u){var c=contrato(id);if(((Number)c.get("empresa_id")).longValue()!=empresaPortal(u)||Set.of("BORRADOR","EN_REVISION").contains(c.get("estado")))throw noExiste();}
 private Map<String,Object> resultadosPortal(long id) {
  // Compatibilidad con el resumen anterior: solo importes en la moneda del contrato.
  // La analítica nueva ofrece las distintas monedas separadas mediante Supabase RLS.
  return CrmPublicidadDemoServicio.resumen(jdbc.queryForList(
   "select sum(m.impresiones) impresiones,sum(m.clics) clics,sum(m.conversiones) conversiones,sum(m.inversion) inversion,sum(m.ingresos) ingresos "
    +ANUNCIOS_PORTAL+" join public.crm_anuncio_metrica m on m.anuncio_id=a.id where visible.id=? and cuenta.moneda=visible.moneda",id));
 }
 public Map<String,Object> portal(UUID u){long empresa=empresaPortal(u);return Map.of("empresa",jdbc.queryForMap("select id,nombre from public.crm_cuenta where id=?",empresa),"contratos",jdbc.queryForList("select id,nombre,servicio,desde,hasta,moneda,honorarios,produccion,presupuesto,estado,alcance from public.crm_acuerdo where empresa_id=? and tipo='CONTRATO' and estado not in ('BORRADOR','EN_REVISION') order by id desc limit 100",empresa));}
 public Map<String,Object> portalContrato(long id,UUID u){exigirContratoPortal(id,u);var c=contrato(id);if(Set.of("BORRADOR","EN_REVISION").contains(c.get("estado")))throw noExiste();return Map.of("resultados",resultadosPortal(id),"prestaciones",alcance.listar(id),"anuncios",jdbc.queryForList("select a.id,a.nombre,a.codigo,a.fecha,a.estado,a.formato "+ANUNCIOS_PORTAL+" where visible.id=? order by a.fecha desc limit 200",id),"revisiones",jdbc.queryForList("select r.id,r.anuncio_id,a.nombre,a.codigo,r.media_id,r.estado,r.observacion,r.version from public.crm_revision_cliente r join public.crm_anuncio a on a.id=r.anuncio_id where a.contrato_id=? and r.estado<>'ANULADA' order by r.id desc limit 200",id),"entregables",jdbc.queryForList("select nombre,descripcion,cantidad,estado,fecha from public.crm_trabajo_servicio where contrato_id=? and tipo='ENTREGABLE' order by fecha limit 200",id),"informes",versiones(id,u,true));}
 @Transactional public void solicitarRevision(long ad,UUID u){long autor=gestionar(u);var a=anuncio(ad);operativo(a);alcance.exigir(((Number)a.get("contrato_id")).longValue(),"PUBLICACION");jdbc.queryForList("select id from public.crm_anuncio where id=? for update",ad);var medios=listaMedios(ad);if(medios.isEmpty())throw invalido("Sube un material antes de solicitar aprobación");jdbc.update("update public.crm_revision_cliente set estado='ANULADA',version=version+1 where anuncio_id=? and estado='RECHAZADO'",ad);jdbc.update("insert into public.crm_revision_cliente(anuncio_id,media_id,solicitado_por) values(?,?,?)",ad,medios.getFirst().get("id"),autor);auditoria.auditar("agencia_anuncios",ad,"SOLICITAR_APROBACION_CLIENTE",autor);}
 @Transactional public void responder(long revision,Map<String,Object> d,UUID u) {
  long empresa=empresaPortal(u);
  var filas=jdbc.queryForList("""
   select r.*,c.empresa_id,c.servicio,c.estado contrato_estado,a.contrato_id
   from public.crm_revision_cliente r join public.crm_anuncio a on a.id=r.anuncio_id
   join public.crm_acuerdo c on c.id=a.contrato_id where r.id=? for update of r
   """,revision);
  if(filas.isEmpty()||((Number)filas.getFirst().get("empresa_id")).longValue()!=empresa)throw noExiste();
  var fila=filas.getFirst();
  operativo(fila);
  alcance.exigir(((Number)fila.get("contrato_id")).longValue(),"PUBLICACION");
  String estado=opcion(d,"estado","APROBADO","RECHAZADO"),observacion=texto(d,"observacion",2000,estado.equals("RECHAZADO"));
  long usuario=jdbc.queryForObject("select id_usuario from public.usuario where id_supabase=?",Long.class,u);
  if(jdbc.update("update public.crm_revision_cliente set estado=?,observacion=?,respondido_por=?,respondido_en=now(),version=version+1 where id=? and version=? and estado='PENDIENTE'",estado,observacion,usuario,revision,entero(d,"version",0,Integer.MAX_VALUE))==0)
   throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","La revisión ya cambió. Actualiza el portal");
  auditoria.auditar("agencia_anuncios",((Number)fila.get("anuncio_id")).longValue(),"CLIENTE_"+estado,usuario);
 }
}
