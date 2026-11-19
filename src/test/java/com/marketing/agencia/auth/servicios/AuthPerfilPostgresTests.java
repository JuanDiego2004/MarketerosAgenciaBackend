package com.marketing.agencia.auth.servicios;

import com.marketing.agencia.Usuario.repositorio.UsuarioRepositorio;
import com.marketing.agencia.Usuario.dto.Requests.LoginRequest;
import com.marketing.agencia.auth.dto.response.RespuestaAuthSupabase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Solo lectura de perfiles: no usa contraseñas reales ni modifica cuentas. */
@SpringBootTest(properties="crm.automatizaciones.habilitadas=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CRM_PRUEBAS_BD",matches="true")
class AuthPerfilPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired AuthServicio auth;
    @MockitoBean SupabaseAuthServicio proveedor;
    @Test void loginCargaRolYPermisosSinMantenerUnaTransaccionDuranteLaPeticionExterna(){
        var ids=jdbc.queryForList("select id_supabase from usuario where estado='ACTIVO' and id_supabase is not null and id_rol is not null limit 1",UUID.class);
        Assumptions.assumeFalse(ids.isEmpty(),"Se necesita un perfil activo para comprobar la lectura");
        UUID id=ids.getFirst();
        var usuario=usuarios.findByIdSupabase(id).orElseThrow();
        // Fuera de una transacción JPA: el rol y su colección deben estar cargados.
        assertNotNull(usuario.getRol().getNombreRol());int cantidad=usuario.getRol().getPermisos().size();
        var respuesta=new RespuestaAuthSupabase();respuesta.setId(id);respuesta.setTokenAcceso("token-solo-prueba");respuesta.setVenceEn(3600L);
        when(proveedor.iniciarSesion(anyString(),anyString())).thenAnswer(i->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return respuesta;});
        LoginRequest solicitud=new LoginRequest();solicitud.setCorreo("prueba@example.invalid");solicitud.setContrasena("sin-contraseña-real");
        var sesion=auth.login(solicitud);assertEquals("token-solo-prueba",sesion.token());assertNotNull(sesion.usuario().rol());assertTrue(sesion.permisos().size()<=cantidad);assertTrue(sesion.fechaExpiracion().isAfter(java.time.OffsetDateTime.now()));
    }
}
