package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmPublicidadDemoServicio {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    private final CrmOperacionServicio operaciones;
    private final CrmMockarooServicio proveedor;
    private final CrmEvolucionServicio evolucion;
    private final TransactionTemplate tx;
    private static final String CONSULTA="select d.*,c.nombre campana,c.id_cliente,cl.nombre cliente from public.crm_publicidad_demo d join public.campana c on c.id_campana=d.campana_id join public.cliente cl on cl.id_cliente=c.id_cliente";
    private void leer(UUID u){acceso.exigir(u,"atribucion",false);}
    public Map<String,Object> estado(UUID u){
        leer(u);boolean gestionar=true;try{acceso.exigir(u,"integraciones",true);}catch(ExcepcionApi e){gestionar=false;}
        boolean esquema=Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('public.crm_publicidad_demo') is not null and to_regclass('public.crm_publicidad_demo_metrica') is not null and to_regclass('public.crm_inversion') is not null",Boolean.class));
        return Map.of("habilitado",proveedor.habilitado(),"configurado",proveedor.configurado(),"esquemaListo",esquema,"gestionar",gestionar,"paises",CrmMockarooServicio.PAISES,"campanas",gestionar?jdbc.queryForList("select c.id_campana id,c.nombre,c.id_cliente,cl.nombre cliente from public.campana c join public.cliente cl on cl.id_cliente=c.id_cliente order by cl.nombre,c.nombre limit 1000"):List.of());
    }
    public List<Map<String,Object>> listar(UUID u){leer(u);return jdbc.queryForList(CONSULTA+" order by d.id desc limit 1000");}
    @Transactional public Map<String,Object> crear(Map<String,Object>d,UUID u){
        long autor=acceso.exigir(u,"integraciones",true),campana=numero(d,"campana_id");String plataforma=opcion(d,"plataforma","META","TIKTOK","YOUTUBE"),moneda=opcion(d,"moneda","PEN","USD");
        if(jdbc.queryForObject("select count(*) from public.campana where id_campana=?",Long.class,campana)==0)throw invalido("Selecciona una campaña interna existente");
        String codigo=plataforma+"-"+campana;
        long id=jdbc.queryForObject("insert into public.crm_publicidad_demo(campana_id,codigo,plataforma,moneda,creado_por) values(?,?,?,?,?) returning id",Long.class,campana,codigo,plataforma,moneda,autor);
        operaciones.auditar("integraciones",id,"CREAR_PUBLICIDAD_DEMO",autor);return Map.of("id",id,"codigo",codigo);
    }
    public Map<String,Object> sincronizar(long id,LocalDate desde,LocalDate hasta,UUID u){
        long autor=acceso.exigir(u,"integraciones",true);periodo(desde,hasta);encontrar(id);
        Set<LocalDate> completos=new HashSet<>(jdbc.queryForList("select fecha from public.crm_publicidad_demo_metrica where demo_id=? and fecha between ? and ? group by fecha having count(*)=9",LocalDate.class,id,desde,hasta));
        List<LocalDate> pendientes=desde.datesUntil(hasta.plusDays(1)).filter(f->!completos.contains(f)).toList();
        if(pendientes.isEmpty())return Map.of("filasNuevas",0,"diasNuevos",0,"reutilizado",true);
        try{
            var filas=proveedor.generar(pendientes);
            Integer nuevas=tx.execute(s->{
                jdbc.queryForList("select id from public.crm_publicidad_demo where id=? for update",id);
                int n=0;for(var f:filas)n+=jdbc.update("insert into public.crm_publicidad_demo_metrica(demo_id,fecha,pais,impresiones,clics,conversiones,inversion,ingresos) values(?,?,?,?,?,?,?,?) on conflict do nothing",id,f.fecha(),f.pais(),f.impresiones(),f.clics(),f.conversiones(),f.inversion(),f.ingresos());
                jdbc.update("update public.crm_publicidad_demo set sincronizado_en=now(),ultimo_error=null where id=?",id);operaciones.auditar("integraciones",id,"SINCRONIZAR_MOCKAROO",autor);return n;
            });
            return Map.of("filasNuevas",Objects.requireNonNull(nuevas),"diasNuevos",nuevas/9,"reutilizado",nuevas==0);
        }catch(RuntimeException e){jdbc.update("update public.crm_publicidad_demo set ultimo_error=? where id=?","No se completó la sincronización. Los datos anteriores se conservan. Revisa configuración y límite de Mockaroo.",id);throw e;}
    }
    public Map<String,Object> metricas(long id,LocalDate desde,LocalDate hasta,String pais,UUID u){
        leer(u);periodo(desde,hasta);String filtro=Objects.toString(pais,"");if(!filtro.isEmpty()&&CrmMockarooServicio.PAISES.stream().noneMatch(p->p.codigo().equals(filtro)))throw invalido("País inválido");
        var campana=encontrar(id);
        var filas=jdbc.queryForList("select fecha,pais,impresiones,clics,conversiones,inversion,ingresos from public.crm_publicidad_demo_metrica where demo_id=? and fecha between ? and ? and (?='' or pais=?) order by fecha,pais",id,desde,hasta,filtro,filtro);
        List<Map<String,Object>> paises=new ArrayList<>();for(var p:CrmMockarooServicio.PAISES){var grupo=filas.stream().filter(f->p.codigo().equals(f.get("pais"))).toList();if(!grupo.isEmpty()){Map<String,Object> datos=new LinkedHashMap<>(resumen(grupo));datos.put("codigo",p.codigo());datos.put("nombre",p.nombre());datos.put("latitud",p.latitud());datos.put("longitud",p.longitud());paises.add(datos);}}
        Set<String> fechas=new TreeSet<>();filas.forEach(f->fechas.add(f.get("fecha").toString()));List<Map<String,Object>>dias=new ArrayList<>();
        for(String fecha:fechas){Map<String,Object>dia=new LinkedHashMap<>(resumen(filas.stream().filter(f->fecha.equals(f.get("fecha").toString())).toList()));dia.put("fecha",fecha);dias.add(dia);}
        return Map.of("simulado",true,"proveedor","MOCKAROO","campana",campana,"resumen",resumen(filas),"dias",dias,"paises",paises,"desde",desde,"hasta",hasta,"negocio",negocio(campana,desde,hasta));
    }
    private Map<String,Object> negocio(Map<String,Object>campana,LocalDate desde,LocalDate hasta){
        long id=((Number)campana.get("campana_id")).longValue();String moneda=campana.get("moneda").toString();BigDecimal ingreso=BigDecimal.ZERO,ventas=BigDecimal.ZERO,gasto=BigDecimal.ZERO;
        for(var f:evolucion.ingresos(desde,hasta,"PRIMERO"))if(Objects.equals(f.get("campana_id"),id)&&moneda.equals(f.get("moneda"))){ingreso=ingreso.add(new BigDecimal(f.get("ingresos").toString()));ventas=ventas.add(new BigDecimal(f.get("ventas").toString()));}
        for(var f:operaciones.gastosCampanas(desde,hasta))if(Objects.equals(f.get("campana_id"),id)&&moneda.equals(f.get("moneda")))gasto=gasto.add(new BigDecimal(f.get("gasto").toString()));
        long leads=jdbc.queryForObject("select count(*) from public.crm_contacto c where coalesce((select x.campana_id from public.crm_origen x where x.contacto_id=c.id order by x.created_at,x.id limit 1),c.campana_id)=? and (c.created_at at time zone 'America/Lima')::date between ? and ?",Long.class,id,desde,hasta);
        Map<String,Object> r=new LinkedHashMap<>();r.put("ingresos",ingreso);r.put("ventas",ventas);r.put("leads",leads);r.put("inversion",gasto);r.put("moneda",moneda);r.put("modelo","PRIMERO");
        r.put("cpl",leads==0?null:gasto.divide(BigDecimal.valueOf(leads),4,RoundingMode.HALF_UP));r.put("roas",gasto.signum()==0?null:ingreso.divide(gasto,4,RoundingMode.HALF_UP));r.put("roi",gasto.signum()==0?null:ingreso.subtract(gasto).multiply(BigDecimal.valueOf(100)).divide(gasto,4,RoundingMode.HALF_UP));
        r.put("inversiones",jdbc.queryForList("select id,fecha,concepto,importe,moneda,anulado,version from public.crm_inversion where campana_id=? and moneda=? and fecha between ? and ? order by fecha desc,id desc limit 100",id,moneda,desde,hasta));return r;
    }
    @Transactional public Map<String,Object> inversion(Map<String,Object>d,UUID u){
        long autor=acceso.exigir(u,"integraciones",true),campana=numero(d,"campana_id");String moneda=opcion(d,"moneda","PEN","USD"),concepto=texto(d,"concepto",200,true);BigDecimal importe=decimal(d,"importe");LocalDate fecha;UUID clave;
        try{fecha=LocalDate.parse(Objects.toString(d.get("fecha"),""));clave=UUID.fromString(Objects.toString(d.get("idempotencia"),""));}catch(Exception e){throw invalido("Revisa la fecha y vuelve a abrir el formulario");}
        if(importe.signum()<=0||importe.compareTo(new BigDecimal("999999999"))>0||fecha.isAfter(LocalDate.now(ZoneId.of("America/Lima"))))throw invalido("Indica un importe positivo y una fecha sin anticipación");
        if(jdbc.queryForObject("select count(*) from public.campana where id_campana=?",Long.class,campana)==0)throw invalido("Campaña inexistente");
        var nuevo=jdbc.queryForList("insert into public.crm_inversion(campana_id,fecha,moneda,concepto,importe,idempotencia,creado_por) values(?,?,?,?,?,?,?) on conflict(idempotencia) do nothing returning id",campana,fecha,moneda,concepto,importe,clave,autor);
        if(!nuevo.isEmpty()){long id=((Number)nuevo.getFirst().get("id")).longValue();operaciones.auditar("integraciones",id,"REGISTRAR_INVERSION",autor);return Map.of("id",id);}
        var anterior=jdbc.queryForMap("select * from public.crm_inversion where idempotencia=?",clave);
        if(!Objects.equals(anterior.get("campana_id"),campana)||!moneda.equals(anterior.get("moneda"))||!concepto.equals(anterior.get("concepto"))||!fecha.toString().equals(anterior.get("fecha").toString())||importe.compareTo(new BigDecimal(anterior.get("importe").toString()))!=0||!Objects.equals(anterior.get("creado_por"),autor))throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","La solicitud ya se utilizó con otros datos. Reabre el formulario.");
        return Map.of("id",anterior.get("id"));
    }
    @Transactional public void anularInversion(long id,int version,UUID u){long autor=acceso.exigir(u,"integraciones",true);if(jdbc.update("update public.crm_inversion set anulado=true,version=version+1 where id=? and version=? and not anulado",id,version)==0)throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","El gasto cambió o ya estaba anulado. Actualiza los resultados.");operaciones.auditar("integraciones",id,"ANULAR_INVERSION",autor);}
    private Map<String,Object> encontrar(long id){var filas=jdbc.queryForList(CONSULTA+" where d.id=?",id);if(filas.isEmpty())throw new ExcepcionApi(HttpStatus.NOT_FOUND,"PUBLICIDAD_DEMO_NO_EXISTE","Campaña publicitaria inexistente");return filas.getFirst();}
    static void periodo(LocalDate desde,LocalDate hasta){if(desde==null||hasta==null||hasta.isBefore(desde)||ChronoUnit.DAYS.between(desde,hasta)>=90||hasta.isAfter(LocalDate.now(ZoneId.of("America/Lima"))))throw invalido("Elige entre 1 y 90 días, sin fechas futuras");}
    static Map<String,Object> resumen(List<Map<String,Object>>filas){
        Map<String,Object>r=new LinkedHashMap<>();for(String campo:List.of("impresiones","clics","conversiones","inversion","ingresos")){BigDecimal total=filas.stream().map(f->new BigDecimal(f.get(campo).toString())).reduce(BigDecimal.ZERO,BigDecimal::add);r.put(campo,total);}
        r.put("ctr",ratio(r,"clics","impresiones",100));r.put("cpc",ratio(r,"inversion","clics",1));r.put("cpm",ratio(r,"inversion","impresiones",1000));r.put("cpa",ratio(r,"inversion","conversiones",1));r.put("roas",ratio(r,"ingresos","inversion",1));return r;
    }
    private static BigDecimal ratio(Map<String,Object>r,String a,String b,int factor){BigDecimal divisor=(BigDecimal)r.get(b);return divisor.signum()==0?null:((BigDecimal)r.get(a)).multiply(BigDecimal.valueOf(factor)).divide(divisor,4,RoundingMode.HALF_UP);}
}
