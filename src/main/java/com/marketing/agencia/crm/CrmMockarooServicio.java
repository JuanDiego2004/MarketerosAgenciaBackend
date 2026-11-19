package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.invalido;

/** Proveedor de datos sintéticos. No transmite datos de clientes a Mockaroo. */
@Service @RequiredArgsConstructor
public class CrmMockarooServicio {
    private final CrmProveedorHttp http;
    @Value("${crm.mockaroo.habilitado:false}") private boolean habilitado;
    @Value("${crm.mockaroo.api-key:}") private String apiKey;
    public record Pais(String codigo,String nombre,double latitud,double longitud){}
    public static final List<Pais> PAISES=List.of(
        new Pais("PE","Perú",-9.19,-75.02),new Pais("CO","Colombia",4.57,-74.30),new Pais("MX","México",23.63,-102.55),
        new Pais("ES","España",40.46,-3.75),new Pais("US","Estados Unidos",37.09,-95.71),new Pais("BR","Brasil",-14.24,-51.93),
        new Pais("JP","Japón",36.20,138.25),new Pais("ZA","Sudáfrica",-30.56,22.94),new Pais("AU","Australia",-25.27,133.78));
    public record Fila(LocalDate fecha,String pais,long impresiones,long clics,long conversiones,BigDecimal inversion,BigDecimal ingresos){}
    public boolean habilitado(){return habilitado;}
    public boolean configurado(){return habilitado&&!apiKey.isBlank();}
    public List<Fila> generar(List<LocalDate> dias){
        if(!configurado())throw new ExcepcionApi(HttpStatus.SERVICE_UNAVAILABLE,"MOCKAROO_CONFIGURACION","Configura CRM_MOCKAROO_HABILITADO=true y CRM_MOCKAROO_API_KEY en el entorno del backend");
        int cantidad=dias.size()*PAISES.size();
        if(dias.isEmpty()||dias.size()>90||new HashSet<>(dias).size()!=dias.size())throw invalido("Solicita entre 1 y 90 días distintos");
        JsonNode respuesta=http.post("https://api.mockaroo.com/api/generate.json?count="+cantidad+"&array=true",Map.of("X-API-Key",apiKey,"User-Agent","MarketingSystem/1.0"),campos(),false);
        return interpretar(respuesta,dias);
    }
    static List<Map<String,Object>> campos(){return List.of(Map.of("name","n","type","Row Number"),numero("impresiones",1000,10000),numero("ctr_base",100,600),numero("conversion_base",100,1000),numero("costo_centavos",15,250),numero("ticket_centavos",2000,25000));}
    private static Map<String,Object> numero(String nombre,int min,int max){return Map.of("name",nombre,"type","Number","min",min,"max",max,"decimals",0);}
    /** Agrupa anuncios sin enviar sus identificadores; cada descarga contiene como máximo 990 filas. */
    public Map<Long,List<Fila>> generarAnuncios(Map<Long,List<LocalDate>> solicitud){
        if(!configurado())throw new ExcepcionApi(HttpStatus.SERVICE_UNAVAILABLE,"MOCKAROO_CONFIGURACION","Configura Mockaroo en el entorno del backend");
        record DiaAnuncio(long anuncio,LocalDate fecha){}
        List<DiaAnuncio> plan=new ArrayList<>();Map<Long,List<Fila>> resultado=new LinkedHashMap<>();
        solicitud.forEach((id,dias)->{if(dias.isEmpty()||dias.size()>90||new HashSet<>(dias).size()!=dias.size())throw invalido("Solicita entre 1 y 90 días distintos por anuncio");resultado.put(id,new ArrayList<>());dias.forEach(d->plan.add(new DiaAnuncio(id,d)));});
        for(int inicio=0;inicio<plan.size();inicio+=110){
            var lote=plan.subList(inicio,Math.min(inicio+110,plan.size()));
            var respuesta=http.post("https://api.mockaroo.com/api/generate.json?count="+lote.size()*PAISES.size()+"&array=true",Map.of("X-API-Key",apiKey,"User-Agent","MarketingSystem/1.0"),campos(),false);
            var filas=interpretar(respuesta,lote.stream().map(DiaAnuncio::fecha).toList());
            for(int i=0;i<filas.size();i++){int dia=(respuesta.get(i).path("n").asInt()-1)/PAISES.size();resultado.get(lote.get(dia).anuncio()).add(filas.get(i));}
        }
        return resultado;
    }
    static List<Fila> interpretar(JsonNode respuesta,List<LocalDate>dias){
        int cantidad=dias.size()*PAISES.size();
        if(respuesta==null||!respuesta.isArray()||respuesta.size()!=cantidad)throw invalido("Mockaroo devolvió un conjunto incompleto. No se guardaron resultados parciales");
        Set<Long> vistos=new HashSet<>();List<Fila> filas=new ArrayList<>();
        for(JsonNode r:respuesta){
            long n=natural(r,"n",1,cantidad);if(!vistos.add(n))throw invalido("Mockaroo devolvió filas duplicadas");
            long impresiones=natural(r,"impresiones",1000,10000),ctr=natural(r,"ctr_base",100,600),conversion=natural(r,"conversion_base",100,1000),costo=natural(r,"costo_centavos",15,250),ticket=natural(r,"ticket_centavos",2000,25000);
            long clics=Math.round(impresiones*ctr/10000.0),conversiones=Math.round(clics*conversion/10000.0);
            int indice=(int)n-1;
            filas.add(new Fila(dias.get(indice/PAISES.size()),PAISES.get(indice%PAISES.size()).codigo(),impresiones,clics,conversiones,BigDecimal.valueOf(clics*costo,2),BigDecimal.valueOf(conversiones*ticket,2)));
        }
        return List.copyOf(filas);
    }
    private static long natural(JsonNode r,String campo,long min,long max){
        JsonNode v=r.path(campo);if(!v.isNumber()&&!v.isTextual()||!v.asText().matches("[0-9]{1,9}"))throw invalido("Respuesta numérica de Mockaroo inválida");
        long n=Long.parseLong(v.asText());if(n<min||n>max)throw invalido("Métricas de Mockaroo fuera del rango permitido");return n;
    }
}
