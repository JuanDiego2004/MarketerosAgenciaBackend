package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmGoogleServicio {
    private final JdbcTemplate jdbc;private final CrmAcceso acceso;private final CrmOperacionServicio operaciones;
    private final CrmProveedorHttp http;private final TransactionTemplate tx;
    @Value("${crm.google.client-id:}") private String clientId;
    @Value("${crm.google.client-secret:}") private String clientSecret;
    @Value("${crm.google.refresh-token:}") private String refresh;
    @Value("${crm.google.developer-token:}") private String developer;
    @Value("${crm.google.login-customer-id:}") private String login;
    @Value("${crm.google.ads-version:v26}") private String version;
    private String accessToken="";private Instant vence=Instant.EPOCH;
    public Map<String,Object>estado(UUID u){acceso.exigir(u,"integraciones",false);return Map.of("googleAdsConfigurado",oauth(),"analyticsConfigurado",oauth(),"versionGoogleAds",version);}
    private boolean oauth(){return !clientId.isBlank()&&!clientSecret.isBlank()&&!refresh.isBlank();}
    private synchronized String token(){
        if(!oauth())throw invalido("Configura las credenciales OAuth de Google en el backend");
        if(Instant.now().isBefore(vence))return accessToken;
        JsonNode r=http.post("https://oauth2.googleapis.com/token",Map.of(),Map.of("client_id",clientId,"client_secret",clientSecret,"refresh_token",refresh,"grant_type","refresh_token"),true);
        String t=r.path("access_token").asText();if(t.isBlank()||r.path("expires_in").asLong()<60)throw invalido("Google no devolvió una credencial válida");accessToken=t;vence=Instant.now().plusSeconds(Math.min(3500,r.path("expires_in").asLong()-30));return t;
    }
    public Map<String,Object>listar(int pagina,UUID u){acceso.exigir(u,"integraciones",false);if(pagina<0||pagina>10000)throw invalido("Página inválida");return Map.of("filas",jdbc.queryForList("select c.*,p.nombre campana from public.crm_conector c join public.campana p on p.id_campana=c.campana_id order by c.id desc limit 25 offset ?",pagina*25),"total",jdbc.queryForObject("select count(*) from public.crm_conector",Long.class));}
    @Transactional public Map<String,Object>crear(Map<String,Object>d,UUID u){
        long autor=acceso.exigir(u,"integraciones",true);String fuente=opcion(d,"fuente","GOOGLE_ADS","GA4"),cuenta=texto(d,"cuenta_externa",30,true),campana=texto(d,"campana_externa",200,true);long interna=numero(d,"campana_id");
        if(!cuenta.matches("[0-9]{1,30}")||fuente.equals("GOOGLE_ADS")&&!campana.matches("[0-9]{1,30}"))throw invalido("Usa identificadores numéricos sin guiones para Google Ads / propiedad GA4");
        if(jdbc.queryForObject("select count(*) from public.campana where id_campana=?",Long.class,interna)==0)throw invalido("Campaña inexistente");
        long id=jdbc.queryForObject("insert into public.crm_conector(fuente,cuenta_externa,campana_externa,campana_id) values(?,?,?,?) returning id",Long.class,fuente,cuenta,campana,interna);operaciones.auditar("integraciones",id,"CREAR_"+fuente,autor);return Map.of("id",id);
    }
    @Transactional public void activar(long id,int revision,boolean activo,UUID u){long autor=acceso.exigir(u,"integraciones",true);if(jdbc.update("update public.crm_conector set activo=?,version=version+1 where id=? and version=?",activo,id,revision)==0)throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","El vínculo cambió. Recarga la lista.");operaciones.auditar("integraciones",id,activo?"ACTIVAR_CONECTOR":"ARCHIVAR_CONECTOR",autor);}
    public Map<String,Object>sincronizar(long id,LocalDate desde,LocalDate hasta,UUID u){
        long autor=acceso.exigir(u,"integraciones",true);periodo(desde,hasta);var lista=jdbc.queryForList("select * from public.crm_conector where id=? and activo",id);if(lista.isEmpty())throw invalido("Vínculo inexistente o archivado");var c=lista.getFirst();
        try{boolean ads="GOOGLE_ADS".equals(c.get("fuente"));var dias=ads?ads(c,desde,hasta):analytics(c,desde,hasta);
            tx.executeWithoutResult(s->{var actual=jdbc.queryForList("select activo,version from public.crm_conector where id=? for update",id);if(actual.isEmpty()||!Boolean.TRUE.equals(actual.getFirst().get("activo"))||!actual.getFirst().get("version").equals(c.get("version")))throw invalido("El vínculo cambió durante la sincronización; vuelve a cargarlo");
                jdbc.update(ads?"delete from public.crm_gasto_google where conector_id=? and fecha between ? and ?":"delete from public.crm_analitica where conector_id=? and fecha between ? and ?",id,desde,hasta);
                for(var dia:dias)if(ads)jdbc.update("insert into public.crm_gasto_google(conector_id,campana_id,fecha,moneda,importe,impresiones,clicks) values(?,?,?,?,?,?,?)",id,c.get("campana_id"),dia.get("fecha"),dia.get("moneda"),dia.get("importe"),dia.get("impresiones"),dia.get("clicks"));else jdbc.update("insert into public.crm_analitica(conector_id,fecha,usuarios,sesiones,eventos_clave) values(?,?,?,?,?)",id,dia.get("fecha"),dia.get("usuarios"),dia.get("sesiones"),dia.get("eventos"));
                jdbc.update("update public.crm_conector set sincronizado_en=now(),ultimo_error=null where id=?",id);operaciones.auditar("integraciones",id,"SINCRONIZAR_"+c.get("fuente"),autor);});return Map.of("diasSincronizados",dias.size());
        }catch(RuntimeException e){jdbc.update("update public.crm_conector set ultimo_error=? where id=?","Sincronización fallida. Revisa credenciales, permisos y período. No se guardaron datos parciales.",id);throw e;}
    }
    private List<Map<String,Object>>ads(Map<String,Object>c,LocalDate desde,LocalDate hasta){
        if(!version.matches("v[0-9]+")||!login.isBlank()&&!login.matches("[0-9]{1,30}"))throw invalido("Revisa la versión y la cuenta administradora de Google Ads");
        Map<String,String>h=new HashMap<>(Map.of("Authorization","Bearer "+token()));if(!developer.isBlank())h.put("developer-token",developer);if(!login.isBlank())h.put("login-customer-id",login);
        String consulta="SELECT campaign.id, customer.currency_code, segments.date, metrics.cost_micros, metrics.impressions, metrics.clicks FROM campaign WHERE campaign.id = "+c.get("campana_externa")+" AND segments.date BETWEEN '"+desde+"' AND '"+hasta+"'";
        JsonNode r=http.post("https://googleads.googleapis.com/"+version+"/customers/"+c.get("cuenta_externa")+"/googleAds:searchStream",h,Map.of("query",consulta),false);if(!r.isArray())throw invalido("Respuesta de Google Ads inválida");List<Map<String,Object>>dias=new ArrayList<>();
        for(JsonNode bloque:r){if(bloque.has("error"))throw invalido("Google Ads devolvió un error");for(JsonNode fila:bloque.path("results")){LocalDate fecha=LocalDate.parse(fila.path("segments").path("date").asText());validarFecha(fecha,desde,hasta);String moneda=fila.path("customer").path("currencyCode").asText();if(!moneda.matches("[A-Z]{3}"))throw invalido("Moneda inválida de Google Ads");JsonNode m=fila.path("metrics");BigDecimal costo=decimal(m.path("costMicros")).movePointLeft(6);dias.add(Map.of("fecha",fecha,"moneda",moneda,"importe",costo,"impresiones",natural(m.path("impressions")),"clicks",natural(m.path("clicks"))));}}
        if(dias.size()>91)throw invalido("Demasiadas filas de Google Ads");return dias;
    }
    private List<Map<String,Object>>analytics(Map<String,Object>c,LocalDate desde,LocalDate hasta){
        JsonNode r=http.post("https://analyticsdata.googleapis.com/v1beta/properties/"+c.get("cuenta_externa")+":runReport",Map.of("Authorization","Bearer "+token()),Map.of("dateRanges",List.of(Map.of("startDate",desde.toString(),"endDate",hasta.toString())),"dimensions",List.of(Map.of("name","date")),"metrics",List.of(Map.of("name","totalUsers"),Map.of("name","sessions"),Map.of("name","keyEvents")),"dimensionFilter",Map.of("filter",Map.of("fieldName","sessionCampaignName","stringFilter",Map.of("matchType","EXACT","value",c.get("campana_externa"),"caseSensitive",true))),"limit",10000),false);
        if(r.path("rowCount").asLong()>10000||r.has("error"))throw invalido("Analytics requiere un período menor");List<Map<String,Object>>dias=new ArrayList<>();
        for(JsonNode fila:r.path("rows")){LocalDate fecha=LocalDate.parse(fila.path("dimensionValues").path(0).path("value").asText(),DateTimeFormatter.BASIC_ISO_DATE);validarFecha(fecha,desde,hasta);var m=fila.path("metricValues");dias.add(Map.of("fecha",fecha,"usuarios",natural(m.path(0).path("value")),"sesiones",natural(m.path(1).path("value")),"eventos",decimal(m.path(2).path("value"))));}if(dias.size()>91)throw invalido("Demasiadas filas de Analytics");return dias;
    }
    public List<Map<String,Object>>metricas(long id,LocalDate desde,LocalDate hasta,UUID u){acceso.exigir(u,"integraciones",false);periodo(desde,hasta);return jdbc.queryForList("select a.fecha,a.usuarios,a.sesiones,a.eventos_clave from public.crm_analitica a where conector_id=? and fecha between ? and ? order by fecha desc",id,desde,hasta);}
    static BigDecimal decimal(JsonNode n){BigDecimal v=new BigDecimal(n.isMissingNode()?"0":n.asText());if(v.signum()<0||v.precision()>20||v.scale()>6)throw invalido("Métrica del proveedor inválida");return v;}
    static long natural(JsonNode n){return decimal(n).longValueExact();}
    static void periodo(LocalDate desde,LocalDate hasta){if(desde==null||hasta==null||hasta.isBefore(desde)||desde.plusDays(90).isBefore(hasta)||hasta.isAfter(LocalDate.now(ZoneOffset.UTC)))throw invalido("Elige un período de hasta 90 días y sin fechas futuras");}
    private static void validarFecha(LocalDate f,LocalDate desde,LocalDate hasta){if(f.isBefore(desde)||f.isAfter(hasta))throw invalido("El proveedor devolvió una fecha fuera del período");}
}
