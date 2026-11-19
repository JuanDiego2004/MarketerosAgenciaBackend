package com.marketing.agencia.crm;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrmEstadisticasInicialesTests {
 @Test void rechazaRespuestaParcialODuplicadaAntesDePersistir(){
  var dia=LocalDate.of(2026,1,1);var plan=Map.of(1L,List.of(dia));
  List<CrmMockarooServicio.Fila> filas=new ArrayList<>();
  for(var pais:CrmMockarooServicio.PAISES)filas.add(new CrmMockarooServicio.Fila(dia,pais.codigo(),2000,60,3,BigDecimal.TEN,BigDecimal.ONE));
  assertDoesNotThrow(()->CrmEstadisticasIniciales.validar(Map.of(1L,filas),plan));
  assertThrows(RuntimeException.class,()->CrmEstadisticasIniciales.validar(Map.of(1L,filas.subList(0,8)),plan));
  filas.set(8,filas.getFirst());assertThrows(RuntimeException.class,()->CrmEstadisticasIniciales.validar(Map.of(1L,filas),plan));
  assertThrows(RuntimeException.class,()->CrmEstadisticasIniciales.validar(Map.of(),plan));
 }
}
