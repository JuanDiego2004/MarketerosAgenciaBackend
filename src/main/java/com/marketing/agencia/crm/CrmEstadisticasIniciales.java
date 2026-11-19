package com.marketing.agencia.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.invalido;

/** Carga del escenario inicial, después del commit del contrato y sin alterar su estado operativo. */
@Service @RequiredArgsConstructor
public class CrmEstadisticasIniciales {
 private final JdbcTemplate jdbc;
 private final CrmAcceso acceso;
 private final CrmMockarooServicio mockaroo;
 private final CrmAgenciaServicio agencia;
 private final TransactionTemplate tx;

 public Map<String,Object> preparar(long contrato,UUID sujeto){
  long autor=acceso.exigir(sujeto,"cuentas",true);
  var acuerdos=jdbc.queryForList("select desde,hasta from public.crm_acuerdo where id=? and tipo='CONTRATO'",contrato);
  if(acuerdos.isEmpty())throw invalido("Contrato inexistente");
  LocalDate inicio=LocalDate.parse(acuerdos.getFirst().get("desde").toString()),fin=LocalDate.parse(acuerdos.getFirst().get("hasta").toString()),hoy=LocalDate.now(ZoneId.of("America/Lima"));
  if(inicio.isAfter(hoy))return Map.of("estado","FECHA_FUTURA","filasNuevas",0,"mensaje","El contrato todavía no inicia; sus estadísticas estarán disponibles dentro de su vigencia");
  if(fin.isAfter(hoy))fin=hoy;
  LocalDate desde=fin.minusDays(6).isBefore(inicio)?inicio:fin.minusDays(6);
  var dias=desde.datesUntil(fin.plusDays(1)).toList();
  var anuncios=jdbc.queryForList("select distinct a.id from public.crm_anuncio a left join public.crm_contrato_anuncio v on v.anuncio_id=a.id where a.contrato_id=? or v.contrato_id=? order by a.id",contrato,contrato);
  if(anuncios.isEmpty())return Map.of("estado","SIN_ANUNCIOS","filasNuevas",0);
  Map<Long,List<LocalDate>> faltantes=new LinkedHashMap<>();
  Map<Long,Boolean> organicos=new HashMap<>();
  for(var anuncio:anuncios){
   long id=((Number)anuncio.get("id")).longValue();
   var completos=new HashSet<>(jdbc.queryForList("select fecha from public.crm_anuncio_metrica where anuncio_id=? and fecha between ? and ? group by fecha having count(*)=9",LocalDate.class,id,desde,fin));
   var pendientes=dias.stream().filter(d->!completos.contains(d)).toList();
   if(!pendientes.isEmpty()){faltantes.put(id,pendientes);organicos.put(id,agencia.organico(id));}
  }
  if(faltantes.isEmpty())return Map.of("estado","LISTO","filasNuevas",0);
  try{
   // HTTP fuera de la transacción. Un lote incompleto nunca se persiste parcialmente.
   var datos=mockaroo.generarAnuncios(faltantes);
   validar(datos,faltantes);
   Integer nuevos=tx.execute(s->{
    int n=0;
    for(var entrada:datos.entrySet())for(var f:entrada.getValue())n+=jdbc.update("insert into public.crm_anuncio_metrica values(?,?,?,?,?,?,?,?) on conflict do nothing",entrada.getKey(),f.fecha(),f.pais(),f.impresiones(),f.clics(),f.conversiones(),organicos.get(entrada.getKey())?BigDecimal.ZERO:f.inversion(),f.ingresos());
    jdbc.update("insert into public.crm_contrato_evento(contrato_id,accion,autor_id) values(?,'ESTADISTICAS_INICIALES_LISTAS',?)",contrato,autor);
    return n;
   });
   return Map.of("estado","LISTO","filasNuevas",Objects.requireNonNull(nuevos));
  }catch(RuntimeException e){
   // El contrato ya está confirmado: conservarlo y permitir reintentar desde sus estadísticas.
   jdbc.update("insert into public.crm_contrato_evento(contrato_id,accion,autor_id) values(?,'ESTADISTICAS_INICIALES_ERROR',?)",contrato,autor);
   return Map.of("estado","ERROR","filasNuevas",0,"mensaje","El contrato está guardado, pero no se pudieron cargar las estadísticas de Mockaroo. Puedes reintentar");
  }
 }
 static void validar(Map<Long,List<CrmMockarooServicio.Fila>> datos,Map<Long,List<LocalDate>> solicitud){
  if(datos==null||!datos.keySet().equals(solicitud.keySet()))throw invalido("Mockaroo no devolvió todos los anuncios");
  for(var e:solicitud.entrySet()){
   var filas=datos.get(e.getKey());Set<String> claves=new HashSet<>();
   if(filas==null||filas.size()!=e.getValue().size()*CrmMockarooServicio.PAISES.size())throw invalido("Mockaroo devolvió estadísticas incompletas");
   for(var f:filas)if(!e.getValue().contains(f.fecha())||CrmMockarooServicio.PAISES.stream().noneMatch(p->p.codigo().equals(f.pais()))||!claves.add(f.fecha()+"/"+f.pais()))throw invalido("Mockaroo devolvió fechas o países inválidos");
  }
 }
}
