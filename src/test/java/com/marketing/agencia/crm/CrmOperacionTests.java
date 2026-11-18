package com.marketing.agencia.crm;
import com.marketing.agencia.config.ExcepcionApi;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CrmOperacionTests {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final CrmAcceso acceso=mock(CrmAcceso.class);
    final CrmOperacionServicio servicio=new CrmOperacionServicio(jdbc,acceso,mock(CrmEvolucionServicio.class));
    final UUID sujeto=UUID.randomUUID();
    @BeforeEach void preparar(){when(acceso.exigir(eq(sujeto),anyString(),anyBoolean())).thenReturn(7L);when(jdbc.queryForObject(contains("lower(r.nombre_rol)"),eq(String.class),eq(7L))).thenReturn("analista");}
    @Test void noPermiteEditarTareaAjena(){when(jdbc.queryForList(contains("for update"),eq(1L))).thenReturn(List.of(Map.of("responsable_id",9L)));assertThrows(ExcepcionApi.class,()->servicio.guardar("actividades",1L,Map.of(),sujeto));verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void analistaNoApruebaContenido(){
        when(jdbc.queryForObject(contains("count(*) from public.usuario"),eq(Long.class),eq(7L))).thenReturn(1L);
        when(jdbc.queryForList(contains("for update"),eq(1L))).thenReturn(List.of(Map.of("responsable_id",7L,"estado","BORRADOR")));
        assertThrows(ExcepcionApi.class,()->servicio.guardar("contenido",1L,Map.of("nombre","Pieza","responsable_id",7,"estado","APROBADO","texto","Hola"),sujeto));
    }
    @Test void importesNoAceptanNegativosNiDecimalesOcultos(){assertThrows(ExcepcionApi.class,()->CrmOperacionServicio.decimal(Map.of("v","-1"),"v"));assertThrows(ExcepcionApi.class,()->CrmOperacionServicio.decimal(Map.of("v","1.001"),"v"));assertEquals("19.99",CrmOperacionServicio.decimal(Map.of("v","19.99"),"v").toString());}
    @Test void idsNoTruncanFracciones(){assertThrows(ExcepcionApi.class,()->CrmOperacionServicio.numero(Map.of("id",1.5),"id"));}
    @Test void noAceptaRecursosFueraDeCatalogo(){assertThrows(ExcepcionApi.class,()->servicio.listar("usuario;drop table",0,"",sujeto));}
    @Test void soloLiderAdminPipelineYAtribucionEsLectura(){
        JdbcTemplate db=mock(JdbcTemplate.class);CrmAcceso permisos=new CrmAcceso(db);
        when(db.queryForList(anyString(),eq(sujeto))).thenReturn(List.of(Map.of("id_usuario",7L,"nombre_rol","Analista")));
        when(db.queryForObject(anyString(),eq(Integer.class),eq(7L),anyString())).thenReturn(1);
        assertThrows(ExcepcionApi.class,()->permisos.exigir(sujeto,"pipeline",false));assertThrows(ExcepcionApi.class,()->permisos.exigir(sujeto,"integraciones",true));
        assertEquals(7L,permisos.exigir(sujeto,"actividades",true));assertEquals(7L,permisos.exigir(sujeto,"atribucion",false));assertThrows(ExcepcionApi.class,()->permisos.exigir(sujeto,"atribucion",true));
    }
}
