package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import com.marketing.agencia.administrador.AdministradorServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

/** Contratación de servicios y ejecución local. Ninguna operación publica en redes. */
@Service @RequiredArgsConstructor
public class CrmAgenciaServicio {
 private final JdbcTemplate jdbc;
 private final CrmAcceso acceso;
 private final CrmOperacionServicio operaciones;
 private final CrmMockarooServicio mockaroo;
 private final TransactionTemplate tx;
 private final CrmAlcanceServicio alcance;
 private final AdministradorServicio administrador;
 private static final Map<String,String> TABLAS=Map.of("cotizaciones","crm_acuerdo","contratos","crm_acuerdo","cuentas-publicitarias","crm_cuenta_publicitaria","anuncios","crm_anuncio","entregables","crm_trabajo_servicio","recomendaciones","crm_trabajo_servicio","ajustes","crm_trabajo_servicio","informes","crm_trabajo_servicio","servicios","crm_servicio");
 private static final Map<String,String> TIPOS=Map.of("entregables","ENTREGABLE","recomendaciones","RECOMENDACION","ajustes","AJUSTE","informes","INFORME");
 private String tabla(String r){if(!TABLAS.containsKey(r))throw new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_RECURSO_INVALIDO","Sección inexistente");return "public."+TABLAS.get(r);}
 private long permiso(UUID u,boolean escribir){return acceso.exigir(u,escribir?"cuentas":"atribucion",escribir);}
 private boolean gestionar(UUID u){try{permiso(u,true);return true;}catch(ExcepcionApi e){return false;}}
 boolean organico(long anuncio){return Boolean.TRUE.equals(jdbc.queryForObject("select case when a.estado='EXTERNO_SIMULADO' then false else coalesce((select canal->>'modalidad'='ORGANICA' from public.crm_contrato_config cfg cross join lateral jsonb_array_elements(cfg.datos->'piezas') pieza cross join lateral jsonb_array_elements(pieza->'canales') canal where cfg.contrato_id=a.contrato_id and canal->>'anuncio_id'=a.id::text limit 1),c.modalidad='ORGANICA' and c.servicio<>'ANALISIS') end from public.crm_anuncio a join public.crm_acuerdo c on c.id=a.contrato_id where a.id=?",Boolean.class,anuncio));}
 @Transactional public Map<String,Object> escenarios(UUID u){
  long autor=acceso.exigir(u,"integraciones",true);jdbc.queryForList("select pg_advisory_xact_lock(781324990)");
  var canales=jdbc.queryForList("select id_canal from public.campana where id_canal is not null limit 1");if(canales.isEmpty())throw invalido("Registra un canal de difusión y una campaña antes de preparar escenarios");
  int creados=0;String[] servicios={"DIFUSION","ANALISIS","INTEGRAL"},nombres={"Comercial Andina","Calzados del Valle","Café Mantaro"},plataformas={"FACEBOOK","TIKTOK","INSTAGRAM"};
  for(int i=0;i<3;i++){
   String codigo="ESCENARIO-AD-DEMO-"+(i+1);if(jdbc.queryForObject("select count(*) from public.crm_anuncio where codigo=?",Integer.class,codigo)>0)continue;
   long cliente=jdbc.queryForObject("insert into public.cliente(nombre,estado) values(?,'ACTIVO') returning id_cliente",Long.class,nombres[i]+" · demostración");long empresa=jdbc.queryForObject("select id from public.crm_cuenta where cliente_id=? and fusionada_en is null",Long.class,cliente);
   Map<String,Object> d=new LinkedHashMap<>();d.put("empresa_id",empresa);d.put("nombre",nombres[i]+" · "+servicios[i]);d.put("servicio",servicios[i]);d.put("modalidad","PAGADA");d.put("desde",LocalDate.now().minusDays(29).toString());d.put("hasta",LocalDate.now().plusDays(30).toString());d.put("moneda","PEN");d.put("honorarios",i==1?250:500);d.put("produccion",i==1?0:200);d.put("presupuesto",1000);d.put("responsable_id",autor);d.put("plataformas",plataformas[i]);d.put("alcance",i==1?"Análisis semanal, recomendaciones e informe mensual":"2 videos, 4 publicaciones y 1 informe mensual");d.put("frecuencia","Mensual");d.put("condiciones","Escenario de demostración. Todas las operaciones publicitarias se registran dentro del CRM.");
   long cotizacion=((Number)guardar("cotizaciones",null,d,u).get("id")).longValue();estado("cotizaciones",cotizacion,Map.of("estado","EN_REVISION","version",0),u);estado("cotizaciones",cotizacion,Map.of("estado","APROBADO","version",1),u);d.put("origen_id",cotizacion);
   long contrato=((Number)guardar("contratos",null,d,u).get("id")).longValue();estado("contratos",contrato,Map.of("estado","EN_REVISION","version",0),u);estado("contratos",contrato,Map.of("estado","APROBADO","version",1),u);estado("contratos",contrato,Map.of("estado","ACTIVO","version",2),u);
   long campana=jdbc.queryForObject("insert into public.campana(nombre,id_cliente,id_canal,objetivo,presupuesto_asignado,fecha_inicio,fecha_fin,estado,id_usuario_creador) values(?,?,?,'Escenario de demostración',1000,?,?,'ACTIVA',?) returning id_campana",Long.class,"Promoción · "+nombres[i],cliente,canales.getFirst().get("id_canal"),LocalDate.now().minusDays(29),LocalDate.now().plusDays(30),autor);vincular(contrato,campana,u);
   long cuenta=((Number)guardar("cuentas-publicitarias",null,Map.of("empresa_id",empresa,"nombre",nombres[i]+" · "+plataformas[i],"codigo","ESCENARIO-ACC-DEMO-"+(i+1),"plataforma",plataformas[i],"moneda","PEN"),u).get("id")).longValue();
   Map<String,Object> ad=new LinkedHashMap<>(Map.of("contrato_id",contrato,"campana_id",campana,"cuenta_id",cuenta,"nombre","Anuncio · "+nombres[i],"codigo",codigo,"formato","VIDEO","fecha",LocalDate.now().toString()));
   if(i!=1){long contenido=jdbc.queryForObject("insert into public.crm_contenido(nombre,campana_id,responsable_id,estado,texto) values(?,?,?,'APROBADO','Contenido ficticio aprobado del escenario') returning id",Long.class,"Video · "+nombres[i],campana,autor);ad.put("contenido_id",contenido);}
   long anuncio=((Number)guardar("anuncios",null,ad,u).get("id")).longValue();if(i!=1){int v=0;for(String e:List.of("EN_REVISION","APROBADO","PROGRAMADO","PUBLICADO_SIMULADO"))estado("anuncios",anuncio,Map.of("estado",e,"version",v++),u);}
   guardar("entregables",null,Map.of("contrato_id",contrato,"nombre","Informe mensual","descripcion","Entregar resultados, hallazgos y próximas acciones","fecha",LocalDate.now().plusDays(7).toString(),"cantidad",1,"importe",0),u);creados++;
  }return Map.of("escenariosCreados",creados,"mensaje","Las estadísticas se descargan al pulsar Actualizar datos simulados en cada anuncio");
 }
 @Transactional public void catalogo(String codigo,Map<String,Object>d,UUID u){long autor=acceso.exigir(u,"campos",true);if(!(d.get("activo") instanceof Boolean))throw invalido("Indica si el servicio está activo");if(jdbc.update("update public.crm_servicio set activo=? where codigo=?",d.get("activo"),codigo)!=1)throw invalido("Servicio inexistente");operaciones.auditar("agencia_servicios",0,"CATALOGO_"+codigo+"_"+d.get("activo"),autor);}
 public Map<String,Object> opciones(UUID u){
  permiso(u,false);boolean gestiona=gestionar(u);Map<String,Object> o=new LinkedHashMap<>();o.put("gestionar",gestiona);
  boolean catalogo=true;try{acceso.exigir(u,"campos",true);}catch(ExcepcionApi e){catalogo=false;}o.put("catalogoGestionar",catalogo);o.put("servicios",jdbc.queryForList("select * from public.crm_servicio order by codigo"));
  o.put("empresas",gestiona?jdbc.queryForList("select a.id,a.nombre from public.crm_cuenta a join public.cliente c on c.id_cliente=a.cliente_id where a.fusionada_en is null and c.estado='ACTIVO' order by a.nombre limit 1000"):List.of());
  o.put("usuarios",gestiona?jdbc.queryForList("select id_usuario id,nombres nombre from public.usuario where estado='ACTIVO' order by nombres limit 1000"):List.of());
  o.put("audiencias",List.of());o.put("audienciasPermitidas",false);
  if(gestiona)try{o.put("audiencias",administrador.listar("audiencias",u).stream().filter(a->"ACTIVO".equals(a.get("estado"))).toList());o.put("audienciasPermitidas",true);}catch(ExcepcionApi e){if(e.getEstadoHttp()!=HttpStatus.FORBIDDEN)throw e;}
  o.put("objetivos",gestiona?jdbc.queryForList("select distinct objetivo from public.campana where objetivo is not null and btrim(objetivo)<>'' and length(objetivo)<=300 order by objetivo limit 100",String.class):List.of());
  o.put("contratos",jdbc.queryForList("select id,nombre,empresa_id,servicio,estado,moneda,desde,hasta from public.crm_acuerdo where tipo='CONTRATO' order by id desc limit 1000"));
  var cotizaciones=gestiona?jdbc.queryForList("select * from public.crm_acuerdo where tipo='COTIZACION' and estado='APROBADO' order by id desc limit 1000"):List.<Map<String,Object>>of();cotizaciones.forEach(c->c.put("prestaciones",alcance.listar(((Number)c.get("id")).longValue())));o.put("cotizaciones",cotizaciones);
  o.put("campanas",jdbc.queryForList("select c.id_campana id,c.nombre,a.id empresa_id,v.contrato_id from public.campana c join public.crm_cuenta a on a.cliente_id=c.id_cliente and a.fusionada_en is null left join public.crm_acuerdo_campana v on v.campana_id=c.id_campana order by c.nombre limit 1000"));
  o.put("cuentas",jdbc.queryForList("select * from public.crm_cuenta_publicitaria order by nombre limit 1000"));
  o.put("anuncios",jdbc.queryForList("select id,nombre,codigo,contrato_id from public.crm_anuncio order by id desc limit 1000"));
  o.put("contenidos",gestiona?jdbc.queryForList("select id,nombre,campana_id,estado from public.crm_contenido order by id desc limit 1000"):List.of());return o;
 }
 private String filtro(String r){return switch(r){case "cotizaciones"->" and x.tipo='COTIZACION'";case "contratos"->" and x.tipo='CONTRATO'";default->TIPOS.containsKey(r)?" and x.tipo='"+TIPOS.get(r)+"'":"";};}
 public Map<String,Object> listar(String r,int pagina,String q,long contrato,UUID u){
  boolean calendario=r.equals("calendario");if(calendario)r="anuncios";
  permiso(u,false);if(r.equals("cotizaciones")||r.equals("cuentas-publicitarias"))permiso(u,true);
  String t=tabla(r);if(r.equals("servicios"))return Map.of("filas",jdbc.queryForList("select * from "+t+" order by codigo"),"total",3);
  String where=" where 1=1"+filtro(r)+(calendario?" and x.estado<>'EXTERNO_SIMULADO' and exists(select 1 from public.crm_acuerdo a where a.id=x.contrato_id and a.servicio<>'ANALISIS')":"");List<Object> p=new ArrayList<>();
  if(!q.isBlank()){where+=" and (x.nombre ilike ?"+(r.equals("anuncios")||r.equals("cuentas-publicitarias")?" or x.codigo ilike ?":"")+")";p.add("%"+q.substring(0,Math.min(q.length(),160))+"%");if(r.equals("anuncios")||r.equals("cuentas-publicitarias"))p.add(p.getLast());}
  if(contrato>0&&(r.equals("anuncios")||TIPOS.containsKey(r))){where+=" and x.contrato_id=?";p.add(contrato);}
  long total=jdbc.queryForObject("select count(*) from "+t+" x"+where,Long.class,p.toArray());p.add(Math.max(0,pagina)*20);
  var filas=jdbc.queryForList("select x.* from "+t+" x"+where+" order by x.id desc limit 20 offset ?",p.toArray());if(r.equals("contratos")||r.equals("cotizaciones"))filas.forEach(f->f.put("prestaciones",alcance.listar(((Number)f.get("id")).longValue())));return Map.of("filas",filas,"total",total);
 }
 private Map<String,Object> registro(String r,long id){var f=jdbc.queryForList("select x.* from "+tabla(r)+" x where id=?"+filtro(r),id);if(f.isEmpty())throw new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_AGENCIA_NO_EXISTE","Registro no encontrado en el catálogo");return f.getFirst();}
 private Map<String,Object> contrato(long id,boolean operativo){var c=registro("contratos",id);if(operativo&&!Set.of("APROBADO","ACTIVO").contains(c.get("estado")))throw invalido("El contrato debe estar aprobado o activo");return c;}
 private void difusion(Map<String,Object>c){if("ANALISIS".equals(c.get("servicio")))throw invalido("El contrato de solo análisis no permite editar ni publicar anuncios");}
 private void relacion(boolean correcta){if(!correcta)throw invalido("Empresa, contrato, campaña y cuenta deben pertenecer al mismo servicio");}
 private String plataforma(Map<String,Object>d){return opcion(d,"plataforma","FACEBOOK","INSTAGRAM","TIKTOK","YOUTUBE","GOOGLE","OTRO");}
 private long empresa(Map<String,Object>d){long id=numero(d,"empresa_id");if(jdbc.queryForObject("select count(*) from public.crm_cuenta where id=? and fusionada_en is null",Integer.class,id)!=1)throw invalido("Selecciona una empresa existente");return id;}
 private void validarResponsable(long id){if(jdbc.queryForObject("select count(*) from public.usuario where id_usuario=? and estado='ACTIVO'",Integer.class,id)!=1)throw invalido("Responsable inactivo o inexistente");}
 @Transactional public Map<String,Object> guardar(String r,Long id,Map<String,Object>d,UUID u){
  long autor=permiso(u,!Set.of("recomendaciones","informes").contains(r));if(Set.of("recomendaciones","informes").contains(r))acceso.exigir(u,"contenido",true);
  if(r.equals("servicios"))throw invalido("Los tres servicios principales son el catálogo de esta versión");
  Map<String,Object> previo=id==null?null:registro(r,id),m=new LinkedHashMap<>();
  if(r.equals("contratos")&&id!=null&&jdbc.queryForObject("select count(*) from public.crm_contrato_config where contrato_id=?",Integer.class,id)>0)throw invalido("Abre la ficha contextual para añadir elementos; los acuerdos confirmados conservan su configuración");
  if(previo!=null&&!(r.equals("cuentas-publicitarias"))&&!Set.of("BORRADOR","PENDIENTE").contains(previo.get("estado")))throw invalido("Solo se editan borradores o pendientes");
  m.put("nombre",texto(d,"nombre",160,true));
  if(r.equals("cotizaciones")||r.equals("contratos")){
   m.put("tipo",r.equals("contratos")?"CONTRATO":"COTIZACION");m.put("empresa_id",empresa(d));m.put("servicio",opcion(d,"servicio","DIFUSION","ANALISIS","INTEGRAL"));
   if(jdbc.queryForObject("select count(*) from public.crm_servicio where codigo=? and activo",Integer.class,m.get("servicio"))!=1)throw invalido("Servicio inactivo");
   m.put("modalidad",opcion(d,"modalidad","ORGANICA","PAGADA","MIXTA"));m.put("desde",fecha(d,"desde"));m.put("hasta",fecha(d,"hasta"));if(((LocalDate)m.get("hasta")).isBefore((LocalDate)m.get("desde")))throw invalido("La fecha final debe ser posterior al inicio");
   m.put("moneda",opcion(d,"moneda","PEN","USD"));for(String k:List.of("honorarios","presupuesto","produccion"))m.put(k,decimal(d,k));
   if("ORGANICA".equals(m.get("modalidad"))&&((BigDecimal)m.get("presupuesto")).signum()>0)throw invalido("Una modalidad orgánica no tiene presupuesto de pauta pagada");
   long responsable=numero(d,"responsable_id");validarResponsable(responsable);m.put("responsable_id",responsable);
   m.put("alcance",texto(d,"alcance",4000,true));m.put("plataformas",texto(d,"plataformas",300,true));m.put("condiciones",texto(d,"condiciones",4000,false));m.put("frecuencia",texto(d,"frecuencia",80,true));
   Long origen=vacio(d.get("origen_id"))?null:numero(d,"origen_id");if(origen!=null){var o=registro(r.equals("contratos")?"cotizaciones":"cotizaciones",origen);relacion(Objects.equals(o.get("empresa_id"),m.get("empresa_id"))&&"APROBADO".equals(o.get("estado")));}m.put("origen_id",origen);
   if(previo!=null)relacion(Objects.equals(previo.get("empresa_id"),m.get("empresa_id")));
  }else if(r.equals("cuentas-publicitarias")){
   if(id!=null)throw invalido("Las cuentas conservan su identidad; registra otra cuenta para cambiarla");m.put("empresa_id",empresa(d));m.put("plataforma",plataforma(d));m.put("codigo",codigo(d));m.put("moneda",opcion(d,"moneda","PEN","USD"));
  }else if(r.equals("anuncios")){
   var c=contrato(numero(d,"contrato_id"),true);if(id!=null)difusion(c);
   if(id==null)alcance.reservarAnuncio(((Number)c.get("id")).longValue(),"ANALISIS".equals(c.get("servicio")));
   var cuenta=registro("cuentas-publicitarias",numero(d,"cuenta_id"));relacion(Objects.equals(c.get("empresa_id"),cuenta.get("empresa_id"))&&Objects.equals(c.get("moneda"),cuenta.get("moneda")));
   String incluidas=c.get("plataformas").toString().toUpperCase(Locale.ROOT),plataforma=cuenta.get("plataforma").toString();if(!incluidas.contains(plataforma)&&!(incluidas.contains("META")&&Set.of("FACEBOOK","INSTAGRAM").contains(plataforma)))throw invalido("La plataforma no está incluida en el contrato");
   long campana=numero(d,"campana_id");comprobarCampana(c,campana);
   m.put("contrato_id",c.get("id"));m.put("cuenta_id",cuenta.get("id"));m.put("campana_id",campana);m.put("codigo",codigo(d));m.put("formato",opcion(d,"formato","VIDEO","IMAGEN","TEXTO"));m.put("fecha",fecha(d,"fecha"));
   LocalDate prevista=(LocalDate)m.get("fecha");if(prevista.isBefore(LocalDate.parse(c.get("desde").toString()))||prevista.isAfter(LocalDate.parse(c.get("hasta").toString())))throw invalido("La publicación debe estar dentro de las fechas del contrato");
   Long contenido=vacio(d.get("contenido_id"))?null:numero(d,"contenido_id");if("ANALISIS".equals(c.get("servicio"))&&contenido!=null)throw invalido("Solo análisis registra anuncios existentes sin contenido editable");
   if(contenido!=null)relacion(jdbc.queryForObject("select count(*) from public.crm_contenido where id=? and campana_id=?",Integer.class,contenido,campana)==1);m.put("contenido_id",contenido);
  }else if(TIPOS.containsKey(r)){
   var c=contrato(numero(d,"contrato_id"),true);if(r.equals("ajustes"))difusion(c);if(r.equals("entregables")&&"ANALISIS".equals(c.get("servicio"))&&numero(d,"cantidad")<1)throw invalido("Indica los informes acordados");
   m.put("contrato_id",c.get("id"));m.put("tipo",TIPOS.get(r));m.put("descripcion",texto(d,"descripcion",4000,true));m.put("fecha",fecha(d,"fecha"));m.put("cantidad",entero(d,"cantidad",1,10000));m.put("importe",decimal(d,"importe"));
   if(r.equals("informes"))alcance.exigir(((Number)c.get("id")).longValue(),"INFORMES");
   if(r.equals("entregables")&&alcance.listo()&&!vacio(d.get("prestacion"))){String p=opcion(d,"prestacion","PRODUCCION","EDICION","PUBLICACION","ANALISIS","INFORMES");alcance.exigir(((Number)c.get("id")).longValue(),p);m.put("prestacion",p);}
   Long anuncio=vacio(d.get("anuncio_id"))?null:numero(d,"anuncio_id");if(anuncio!=null)relacion(Objects.equals(registro("anuncios",anuncio).get("contrato_id"),c.get("id")));m.put("anuncio_id",anuncio);
  }else throw invalido("Sección inválida");
  if(previo!=null&&m.containsKey("contrato_id"))relacion(Objects.equals(previo.get("contrato_id"),m.get("contrato_id")));
  if(id==null){if(r.equals("anuncios")&&"ANALISIS".equals(contrato(numero(d,"contrato_id"),true).get("servicio")))m.put("estado","EXTERNO_SIMULADO");id=insertar(tabla(r),m);}else{
   var valores=new ArrayList<>(m.values());valores.add(id);valores.add(entero(d,"version",0,Integer.MAX_VALUE));if(jdbc.update("update "+tabla(r)+" set "+String.join(",",m.keySet().stream().map(k->k+"=?").toList())+",version=version+1 where id=? and version=?",valores.toArray())==0)throw conflicto();
  }
  if(r.equals("contratos")||r.equals("cotizaciones"))alcance.guardar(id,m.get("servicio").toString(),d.get("prestaciones")==null&&m.get("origen_id")!=null?alcance.listar(((Number)m.get("origen_id")).longValue()):d.get("prestaciones"));
  operaciones.auditar("agencia_"+r,id,previo==null?"CREAR":"EDITAR",autor);return registro(r,id);
 }
 private String codigo(Map<String,Object>d){String s=texto(d,"codigo",80,true);if(!s.matches("[A-Za-z0-9_-]{3,80}"))throw invalido("Usa un código de 3 a 80 letras, números o guiones");return s;}
 private long insertar(String t,Map<String,Object>m){return jdbc.queryForObject("insert into "+t+"("+String.join(",",m.keySet())+") values("+String.join(",",Collections.nCopies(m.size(),"?"))+") returning id",Long.class,m.values().toArray());}
 private void comprobarCampana(Map<String,Object>c,long campana){relacion(jdbc.queryForObject("select count(*) from public.campana ca join public.crm_cuenta e on e.cliente_id=ca.id_cliente where ca.id_campana=? and e.id=?",Integer.class,campana,c.get("empresa_id"))==1);relacion(jdbc.queryForObject("select count(*) from public.crm_acuerdo_campana where campana_id=? and contrato_id=?",Integer.class,campana,c.get("id"))==1);}
 @Transactional public void vincular(long id,long campana,UUID u){long autor=permiso(u,true);var c=contrato(id,true);relacion(jdbc.queryForObject("select count(*) from public.campana ca join public.crm_cuenta e on e.cliente_id=ca.id_cliente where ca.id_campana=? and e.id=?",Integer.class,campana,c.get("empresa_id"))==1);jdbc.update("insert into public.crm_acuerdo_campana values(?,?) on conflict do nothing",id,campana);comprobarCampana(c,campana);operaciones.auditar("agencia_contratos",id,"VINCULAR_CAMPANA",autor);}
 @Transactional public void estado(String r,long id,Map<String,Object>d,UUID u){
  long autor=permiso(u,true);var x=registro(r,id);String actual=x.get("estado").toString(),nuevo=texto(d,"estado",25,true);List<String> permitidos=new ArrayList<>();
  if(r.equals("contratos")||r.equals("cotizaciones")){
   permitidos=switch(actual){case "BORRADOR"->List.of("EN_REVISION","CANCELADO");case "EN_REVISION"->List.of("APROBADO","BORRADOR","CANCELADO");case "APROBADO"->r.equals("contratos")?List.of("ACTIVO","CANCELADO"):List.of();case "ACTIVO"->List.of("FINALIZADO","CANCELADO");case "FINALIZADO"->List.of("RENOVADO");default->List.of();};
   if(nuevo.equals("RENOVADO")){long reemplazo=numero(d,"renovacion_id");var c=contrato(reemplazo,true);relacion(reemplazo!=id&&Objects.equals(c.get("empresa_id"),x.get("empresa_id")));}
  }else if(r.equals("anuncios")){
   var c=contrato(((Number)x.get("contrato_id")).longValue(),true);difusion(c);
   alcance.exigir(((Number)c.get("id")).longValue(),"PUBLICACION");
   permitidos=switch(actual){case "BORRADOR"->List.of("EN_REVISION","CANCELADO");case "EN_REVISION"->List.of("APROBADO","BORRADOR");case "APROBADO"->List.of("PROGRAMADO","CANCELADO");case "PROGRAMADO"->List.of("PUBLICADO_SIMULADO","CANCELADO");case "PUBLICADO_SIMULADO"->List.of("FINALIZADO");default->List.of();};
   if(Set.of("APROBADO","PROGRAMADO","PUBLICADO_SIMULADO").contains(nuevo)){
    if(Set.of("VIDEO","IMAGEN").contains(x.get("formato"))&&jdbc.queryForObject("select exists(select 1 from public.crm_contrato_config where contrato_id=?)",Boolean.class,c.get("id"))&&!jdbc.queryForObject("select exists(select 1 from public.crm_media_anuncio where anuncio_id=?)",Boolean.class,id))throw invalido("Carga el material pendiente antes de aprobar o programar la difusión");
    if(x.get("contenido_id")==null||jdbc.queryForObject("select count(*) from public.crm_contenido where id=? and estado='APROBADO'",Integer.class,x.get("contenido_id"))!=1)throw invalido("Asocia contenido aprobado antes de programar o publicar");
    if(nuevo.equals("PUBLICADO_SIMULADO")&&LocalDate.parse(x.get("fecha").toString()).isAfter(LocalDate.now(ZoneId.of("America/Lima"))))throw invalido("La publicación aún no llegó a su fecha programada");
   }
  }else if(TIPOS.containsKey(r)){
   var c=contrato(((Number)x.get("contrato_id")).longValue(),true);if(r.equals("ajustes"))difusion(c);
   permitidos=switch(actual){case "PENDIENTE"->List.of("APROBADO","RECHAZADO");case "APROBADO"->List.of("COMPLETADO");default->List.of();};
   if(r.equals("ajustes")&&nuevo.equals("COMPLETADO")){
    if(x.get("anuncio_id")==null)throw invalido("El ajuste necesita un anuncio de referencia");
    // El importe es una propuesta de incremento autorizado de presupuesto, nunca gasto ejecutado.
   }
  }
  if(!permitidos.contains(nuevo))throw invalido("Cambio de estado no permitido: "+actual+" → "+nuevo);
  if(r.equals("entregables")&&nuevo.equals("COMPLETADO"))alcance.completarEntregable(x);
  if(r.equals("anuncios")&&nuevo.equals("PUBLICADO_SIMULADO")&&alcance.listo()){
   boolean requiere=Boolean.TRUE.equals(x.get("requiere_aprobacion_cliente"));boolean historico=jdbc.queryForObject("select exists(select 1 from public.crm_revision_cliente where anuncio_id=?)",Boolean.class,id);
   if((requiere||historico)&&!jdbc.queryForObject("select exists(select 1 from public.crm_revision_cliente where anuncio_id=? and estado='APROBADO' and media_id=(select max(id) from public.crm_media_anuncio where anuncio_id=?))",Boolean.class,id,id))throw invalido("El cliente aún no aprobó la última versión del material");
   if(x.get("programado_en")!=null){Object f=x.get("programado_en");java.time.Instant instante=f instanceof java.sql.Timestamp t?t.toInstant():OffsetDateTime.parse(f.toString()).toInstant();if(instante.isAfter(java.time.Instant.now()))throw invalido("Aún no llegó la hora prevista de difusión");}
  }
  if(jdbc.update("update "+tabla(r)+" set estado=?,version=version+1 where id=? and version=?",nuevo,id,entero(d,"version",0,Integer.MAX_VALUE))==0)throw conflicto();
  if(r.equals("ajustes")&&nuevo.equals("COMPLETADO"))jdbc.update("update public.crm_acuerdo set presupuesto=presupuesto+?,version=version+1 where id=?",x.get("importe"),x.get("contrato_id"));
  operaciones.auditar("agencia_"+r,id,actual+"_A_"+nuevo,autor);
 }
 private ExcepcionApi conflicto(){return new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","El registro cambió. Actualiza y vuelve a abrirlo antes de guardar");}
 public Map<String,Object> sincronizar(long id,LocalDate desde,LocalDate hasta,UUID u){
  long autor=permiso(u,true);CrmPublicidadDemoServicio.periodo(desde,hasta);var a=registro("anuncios",id);var c=contrato(((Number)a.get("contrato_id")).longValue(),true);
  alcance.exigir(((Number)c.get("id")).longValue(),"ANALISIS");
  if(!Set.of("PUBLICADO_SIMULADO","EXTERNO_SIMULADO","FINALIZADO").contains(a.get("estado")))throw invalido("Publica el anuncio simulado antes de importar estadísticas");
  if(desde.isBefore(LocalDate.parse(c.get("desde").toString()))||hasta.isAfter(LocalDate.parse(c.get("hasta").toString())))throw invalido("El período debe estar dentro de las fechas del contrato");
  Set<LocalDate> completos=new HashSet<>(jdbc.queryForList("select fecha from public.crm_anuncio_metrica where anuncio_id=? and fecha between ? and ? group by fecha having count(*)=9",LocalDate.class,id,desde,hasta));
  var dias=desde.datesUntil(hasta.plusDays(1)).filter(f->!completos.contains(f)).toList();if(dias.isEmpty())return Map.of("filasNuevas",0);
  boolean organica=organico(id);var filas=mockaroo.generar(dias);Integer n=tx.execute(s->{int total=0;for(var f:filas)total+=jdbc.update("insert into public.crm_anuncio_metrica values(?,?,?,?,?,?,?,?) on conflict do nothing",id,f.fecha(),f.pais(),f.impresiones(),f.clics(),f.conversiones(),organica?BigDecimal.ZERO:f.inversion(),f.ingresos());operaciones.auditar("agencia_anuncios",id,"SINCRONIZAR_MOCKAROO",autor);return total;});return Map.of("filasNuevas",Objects.requireNonNull(n));
 }
 public Map<String,Object> metricas(long id,LocalDate desde,LocalDate hasta,UUID u){
  permiso(u,false);CrmPublicidadDemoServicio.periodo(desde,hasta);var a=registro("anuncios",id);var c=contrato(((Number)a.get("contrato_id")).longValue(),false);
  var filas=jdbc.queryForList("select fecha,pais,impresiones,clics,conversiones,inversion,ingresos from public.crm_anuncio_metrica where anuncio_id=? and fecha between ? and ? order by fecha,pais",id,desde,hasta);
  List<Map<String,Object>> paises=new ArrayList<>(),dias=new ArrayList<>();for(var p:CrmMockarooServicio.PAISES){var grupo=filas.stream().filter(f->p.codigo().equals(f.get("pais"))).toList();if(!grupo.isEmpty()){Map<String,Object> v=new LinkedHashMap<>(CrmPublicidadDemoServicio.resumen(grupo));v.putAll(Map.of("codigo",p.codigo(),"nombre",p.nombre(),"latitud",p.latitud(),"longitud",p.longitud()));paises.add(v);}}
  for(String fecha:filas.stream().map(f->f.get("fecha").toString()).distinct().toList()){Map<String,Object> v=new LinkedHashMap<>(CrmPublicidadDemoServicio.resumen(filas.stream().filter(f->fecha.equals(f.get("fecha").toString())).toList()));v.put("fecha",fecha);dias.add(v);}
  return Map.of("anuncio",a,"filas",filas,"resumen",CrmPublicidadDemoServicio.resumen(filas),"fuente","Datos simulados con Mockaroo","dias",dias,"paises",paises,"moneda",c.get("moneda"));
 }
 public Map<String,Object> informe(long id,UUID u){permiso(u,false);var c=contrato(id,false);Map<String,Object> r=new LinkedHashMap<>();r.put("contrato",c);r.put("empresa",jdbc.queryForMap("select nombre from public.crm_cuenta where id=?",c.get("empresa_id")));r.put("campanas",jdbc.queryForList("select c.id_campana id,c.nombre from public.campana c join public.crm_acuerdo_campana v on v.campana_id=c.id_campana where v.contrato_id=?",id));r.put("anuncios",jdbc.queryForList("select a.*,coalesce(sum(m.inversion),0) gasto from public.crm_anuncio a left join public.crm_anuncio_metrica m on m.anuncio_id=a.id where (a.contrato_id=? or exists(select 1 from public.crm_contrato_anuncio v where v.anuncio_id=a.id and v.contrato_id=?)) group by a.id order by a.fecha",id,id));r.put("trabajos",jdbc.queryForList("select * from public.crm_trabajo_servicio where contrato_id=? order by fecha,id",id));r.put("historial",jdbc.queryForList("select a.accion,a.created_at,u.nombres autor from public.crm_auditoria a left join public.usuario u on u.id_usuario=a.autor_id where (a.recurso='agencia_contratos' and a.registro_id=?) or (a.recurso='agencia_anuncios' and a.registro_id in(select id from public.crm_anuncio where contrato_id=?)) order by a.created_at desc limit 100",id,id));
  var dias=jdbc.queryForList("select m.fecha,sum(m.impresiones) impresiones,sum(m.clics) clics,sum(m.conversiones) conversiones,sum(m.inversion) inversion,sum(m.ingresos) ingresos from public.crm_anuncio_metrica m join public.crm_anuncio a on a.id=m.anuncio_id where (a.contrato_id=? or exists(select 1 from public.crm_contrato_anuncio v where v.anuncio_id=a.id and v.contrato_id=?)) group by m.fecha order by m.fecha",id,id);
  var resumen=CrmPublicidadDemoServicio.resumen(dias);r.put("resumen",resumen);r.put("dias",dias);r.put("tieneMetricas",!dias.isEmpty());
  BigDecimal gasto=(BigDecimal)resumen.get("inversion");r.put("gasto",gasto);r.put("alertaPresupuesto",((BigDecimal)c.get("presupuesto")).signum()>0&&gasto.compareTo(((BigDecimal)c.get("presupuesto")).multiply(new BigDecimal("0.8")))>=0);r.put("renovacionProxima",!LocalDate.parse(c.get("hasta").toString()).isAfter(LocalDate.now().plusDays(15)));r.put("fuente","Datos simulados con Mockaroo. Honorarios previstos separados del gasto y de las ventas atribuidas; no acreditan cobros.");return r;
 }
}
