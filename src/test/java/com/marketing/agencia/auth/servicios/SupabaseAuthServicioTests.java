package com.marketing.agencia.auth.servicios;

import com.marketing.agencia.config.ExcepcionApi;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseAuthServicioTests {
    HttpServer servidor;
    java.util.concurrent.ExecutorService hilos;
    @BeforeEach void iniciar() throws Exception {
        servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        hilos=Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(hilos);
        servidor.start();
    }
    @AfterEach void cerrar(){servidor.stop(0);hilos.shutdownNow();}
    SupabaseAuthServicio servicio(Duration lectura){return new SupabaseAuthServicio("http://127.0.0.1:"+servidor.getAddress().getPort(),"clave-de-prueba",Duration.ofSeconds(2),lectura);}
    void responder(int estado,String cuerpo){servidor.createContext("/auth/v1/token",e->{
        assertEquals("grant_type=password",e.getRequestURI().getQuery());
        assertEquals("clave-de-prueba",e.getRequestHeaders().getFirst("apikey"));
        e.getRequestBody().readAllBytes();byte[] datos=cuerpo.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(estado,datos.length);e.getResponseBody().write(datos);e.close();
    });}
    @Test void conservaTokenYUsuarioDeAutenticacionCorrecta(){
        UUID id=UUID.randomUUID();responder(200,"{\"access_token\":\"token-prueba\",\"expires_in\":3600,\"user\":{\"id\":\""+id+"\"}}");
        var r=servicio(Duration.ofSeconds(3)).iniciarSesion("persona@example.invalid","contraseña de prueba");assertEquals(id,r.obtenerIdUsuario());assertEquals("token-prueba",r.getTokenAcceso());
    }
    @Test void credencialesIncorrectasNoCreanSesion(){
        responder(400,"{\"error\":\"invalid_credentials\"}");var e=assertThrows(ExcepcionApi.class,()->servicio(Duration.ofSeconds(3)).iniciarSesion("persona@example.invalid","incorrecta"));assertEquals(401,e.getEstadoHttp().value());assertEquals("CREDENCIALES_INVALIDAS",e.getCodigo());
    }
    @Test void proveedorLentoDevuelveErrorRecuperableEnLugarDeEsperarSinLimite(){
        CountDownLatch terminar=new CountDownLatch(1);
        servidor.createContext("/auth/v1/token",e->{try{terminar.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{e.close();}});
        try{var e=assertThrows(ExcepcionApi.class,()->servicio(Duration.ofMillis(150)).iniciarSesion("persona@example.invalid","prueba"));assertEquals(503,e.getEstadoHttp().value());assertEquals("SUPABASE_TIEMPO_ESPERA",e.getCodigo());}finally{terminar.countDown();}
    }
}
