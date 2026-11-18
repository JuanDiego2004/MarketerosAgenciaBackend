package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in explícito: una consulta real de nueve filas, sin datos de clientes ni base de datos. */
@EnabledIfEnvironmentVariable(named="CRM_PRUEBA_MOCKAROO_REAL",matches="true")
class CrmMockarooContratoTests {
    @Test void contratoRealDevuelveNueveFilasCompatibles(){
        var s=new CrmMockarooServicio(new CrmProveedorHttp(new ObjectMapper()));
        ReflectionTestUtils.setField(s,"habilitado",true);ReflectionTestUtils.setField(s,"apiKey",System.getenv("CRM_MOCKAROO_API_KEY"));
        var r=s.generar(List.of(LocalDate.now().minusDays(1)));assertEquals(9,r.size());assertEquals(9,r.stream().map(CrmMockarooServicio.Fila::pais).distinct().count());assertTrue(r.stream().allMatch(f->f.clics()<=f.impresiones()&&f.conversiones()<=f.clics()));
    }
}
