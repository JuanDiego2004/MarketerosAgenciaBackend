package com.marketing.agencia.crm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Prueba opt-in contra la BD configurada. Todos los datos de prueba se revierten. No envía correos. */
@SpringBootTest @Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CRM_PRUEBAS_BD",matches="true")
class CrmPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CrmServicio crm;
    @Autowired CrmOperacionServicio operaciones;
    @Autowired CrmIntegraciones integraciones;
    @Autowired CrmCaptacionControlador captacion;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Test void cicloCrmConRollback(){
        UUID sujeto=jdbc.queryForObject("select u.id_supabase from public.usuario u join public.rol r using(id_rol) where lower(r.nombre_rol) like '%admin%' and upper(u.estado)='ACTIVO' and u.id_supabase is not null limit 1",UUID.class);
        long campana=jdbc.queryForObject("select id_campana from public.campana order by id_campana limit 1",Long.class);
        String origen="PRUEBA-"+UUID.randomUUID();
        long contacto=((Number)crm.guardar("contactos",null,new CrmControlador.Registro("Prueba CRM rollback",UUID.randomUUID()+"@example.test",null,"NUEVO",origen,"premium",null,campana,null,null,null,null),sujeto).get("id")).longValue();
        long responsable=((Number)crm.detalle("contactos",contacto,sujeto).get("responsableId")).longValue();
        long etapa=jdbc.queryForObject("select id from public.crm_etapa where tipo='GANADA' order by orden limit 1",Long.class);
        var oportunidad=new HashMap<String,Object>(Map.of("nombre","Oportunidad prueba rollback","contacto_id",contacto,"etapa_id",etapa,"responsable_id",responsable,"valor","123.45","moneda","PEN","probabilidad",50,"fecha_cierre",java.time.LocalDate.now().toString()));
        long id=((Number)operaciones.guardar("pipeline",null,oportunidad,sujeto).get("id")).longValue();
        assertEquals(100,jdbc.queryForObject("select probabilidad from public.crm_oportunidad where id=?",Integer.class,id));
        oportunidad.put("version",0);oportunidad.put("nombre","Edición prueba rollback");operaciones.guardar("pipeline",id,oportunidad,sujeto);
        assertThrows(com.marketing.agencia.config.ExcepcionApi.class,()->operaciones.guardar("pipeline",id,oportunidad,sujeto));
        long segmento=((Number)operaciones.guardar("segmentos",null,Map.of("nombre","Segmento prueba rollback","estado","NUEVO","etiqueta","premium","origen",origen),sujeto).get("id")).longValue();
        assertTrue(operaciones.miembros(segmento,10000,sujeto).stream().anyMatch(c->((Number)c.get("id")).longValue()==contacto));
        assertTrue(operaciones.exportarSegmento(segmento,sujeto).length>0);
        operaciones.guardar("scoring",null,Map.of("nombre","Puntos prueba rollback","campo","ETIQUETA","valor","premium","puntos",10),sujeto);
        assertTrue(((Number)operaciones.score(contacto,sujeto).get("puntaje")).intValue()>=10);
        operaciones.guardar("metas",null,Map.of("nombre","Meta prueba rollback","campana_id",campana,"desde","2020-01-01","hasta","2030-12-31","leads",100,"ingresos","1000.00","moneda","PEN"),sujeto);
        assertFalse(operaciones.alertas(sujeto).isEmpty());
        assertNotNull(operaciones.atribucion(java.time.LocalDate.now().minusDays(1),java.time.LocalDate.now().plusDays(1),sujeto).get("ingresos"));
        operaciones.guardar("actividades",null,Map.of("nombre","Seguimiento prueba rollback","tipo","LLAMADA","estado","PENDIENTE","fecha",java.time.OffsetDateTime.now().minusHours(1).toString(),"responsable_id",responsable,"contacto_id",contacto),sujeto);
        assertFalse(operaciones.notificaciones(sujeto).isEmpty());
        assertFalse(operaciones.listar("pipeline",0,"prueba rollback",sujeto).isEmpty());
        assertFalse(operaciones.opciones(sujeto,"actividades").isEmpty());
        assertFalse(operaciones.calendario(java.time.LocalDate.now().minusDays(1),java.time.LocalDate.now().plusDays(1),true,sujeto).isEmpty());
        long flujo=((Number)operaciones.guardar("automatizaciones",null,Map.of("nombre","Flujo prueba rollback","segmento_id",segmento,"accion","CREAR_TAREA","asunto","Llamada automática prueba rollback","activa",true),sujeto).get("id")).longValue();
        assertEquals(1,integraciones.ejecutar(flujo,sujeto).get("procesados"));
        assertEquals(0,integraciones.ejecutar(flujo,sujeto).get("procesados"));
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("prueba").header("alg","ES256").subject(sujeto.toString()).build();
        Map<?,?> respuesta=json.convertValue(captacion.crear(Map.of("campana_id",campana,"nombre","Formulario prueba rollback"),jwt),Map.class);
        Map<?,?> formulario=(Map<?,?>)respuesta.get("datos");UUID formularioId=UUID.fromString(formulario.get("id").toString());
        var captura=new CrmCaptacionControlador.Captura("Captura prueba rollback",UUID.randomUUID()+"@example.test",null,UUID.randomUUID(),true,"meta","cpc","campana-prueba",null);
        captacion.webhook(formularioId,formulario.get("secreto").toString(),captura);
        captacion.webhook(formularioId,formulario.get("secreto").toString(),captura);
        assertEquals(1,jdbc.queryForObject("select count(*) from public.crm_origen where idempotencia=?",Integer.class,captura.idempotencia()));
        assertTrue(captacion.formulario(formularioId,Map.of()).contains("Formulario prueba rollback"));
        String externo=Long.toString(System.currentTimeMillis());
        var vinculo=Map.<String,Object>of("nombre","Meta prueba rollback","campana_id",campana,"campana_externa",externo);
        operaciones.guardar("integraciones",null,vinculo,sujeto);
        assertThrows(com.marketing.agencia.config.ExcepcionApi.class,()->operaciones.guardar("integraciones",null,vinculo,sujeto));
    }
}
