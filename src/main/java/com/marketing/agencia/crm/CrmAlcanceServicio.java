package com.marketing.agencia.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmAlcanceServicio {
 private final JdbcTemplate jdbc;
 public boolean listo(){return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('public.crm_alcance_servicio') is not null",Boolean.class));}
 public List<Map<String,Object>> listar(long id){return listo()?jdbc.queryForList("select prestacion,cantidad from public.crm_alcance_servicio where contrato_id=? order by prestacion",id):List.of();}
 public int limite(long id,String prestacion){if(!listo())return 10000;return jdbc.queryForObject("select coalesce(sum(cantidad),0)::int from public.crm_alcance_servicio where contrato_id=? and prestacion=?",Integer.class,id,prestacion);}
 public void exigir(long id,String prestacion){if(limite(id,prestacion)==0)throw invalido("El contrato no incluye la prestación "+prestacion);}
 public void guardar(long id,String servicio,Object valor){
  if(!listo())return;
  if(valor==null){if(listar(id).isEmpty())for(String p:List.of("PRODUCCION","EDICION","PUBLICACION","ANALISIS","INFORMES"))if(!servicio.equals("ANALISIS")||Set.of("ANALISIS","INFORMES").contains(p))jdbc.update("insert into public.crm_alcance_servicio values(?,?,10000)",id,p);return;}
  if(!(valor instanceof List<?> filas)||filas.size()>5||filas.isEmpty())throw invalido("Selecciona al menos una prestación");
  Set<String> vistos=new HashSet<>();List<Map<String,Object>> validas=new ArrayList<>();
  for(Object fila:filas){if(!(fila instanceof Map<?,?> f))throw invalido("Alcance inválido");Map<String,Object>d=new HashMap<>();f.forEach((k,v)->d.put(k.toString(),v));String p=opcion(d,"prestacion","PRODUCCION","EDICION","PUBLICACION","ANALISIS","INFORMES");if(!vistos.add(p)||servicio.equals("ANALISIS")&&!Set.of("ANALISIS","INFORMES").contains(p))throw invalido("Prestación duplicada o incompatible con solo análisis");validas.add(Map.of("prestacion",p,"cantidad",entero(d,"cantidad",1,10000)));}
  jdbc.update("delete from public.crm_alcance_servicio where contrato_id=?",id);for(var f:validas)jdbc.update("insert into public.crm_alcance_servicio values(?,?,?)",id,f.get("prestacion"),f.get("cantidad"));
 }
 public void reservarAnuncio(long contrato,boolean analisis){
  if(!listo())return;jdbc.queryForList("select id from public.crm_acuerdo where id=? for update",contrato);String p=analisis?"ANALISIS":"PUBLICACION";exigir(contrato,p);
  if(jdbc.queryForObject("select count(*) from public.crm_anuncio where contrato_id=? and estado<>'CANCELADO' and ((? and estado='EXTERNO_SIMULADO') or (not ? and estado<>'EXTERNO_SIMULADO'))",Integer.class,contrato,analisis,analisis)>=limite(contrato,p))throw invalido("Se alcanzó la cantidad de anuncios incluida en "+p);
 }
 public void completarEntregable(Map<String,Object>x){if(x.get("prestacion")==null)return;long id=((Number)x.get("contrato_id")).longValue();String p=x.get("prestacion").toString();jdbc.queryForList("select id from public.crm_acuerdo where id=? for update",id);exigir(id,p);int usados=jdbc.queryForObject("select coalesce(sum(cantidad),0)::int from public.crm_trabajo_servicio where contrato_id=? and tipo='ENTREGABLE' and prestacion=? and estado='COMPLETADO'",Integer.class,id,p);if(usados+((Number)x.get("cantidad")).intValue()>limite(id,p))throw invalido("Los entregables completados superarían la cantidad acordada de "+p);}
}
