package com.marketing.agencia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=CorsRenderTests.Endpoints.class,properties="supabase.url=https://example.supabase.co")
@Import({SeguridadConfig.class,CorsRenderTests.Endpoints.class})
class CorsRenderTests {
 static final String FRONTEND="https://marketerosfrontend.onrender.com";
 @Autowired MockMvc mvc;
 @MockitoBean JwtDecoder jwt;
 @RestController static class Endpoints {
  @PostMapping("/auth/cors-check") String comprobar(){return "ok";}
 }
 @Test void frontendPublicadoHacePreflightDeLogin()throws Exception{
  mvc.perform(options("/auth/login").header("Origin",FRONTEND).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type"))
   .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin",FRONTEND))
   .andExpect(header().string("Access-Control-Allow-Methods",containsString("POST")))
   .andExpect(header().string("Access-Control-Allow-Headers",containsString("authorization")))
   .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
 }
 @Test void frontendPublicadoHacePreflightDelCrm()throws Exception{
  mvc.perform(options("/api/crm/agencia/opciones").header("Origin",FRONTEND).header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","authorization"))
   .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin",FRONTEND));
 }
 @Test void conservaAccesoLocalDeDesarrollo()throws Exception{
  mvc.perform(options("/auth/login").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","POST"))
   .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
 }
 @Test void origenAjenoNoSeAutoriza()throws Exception{
  mvc.perform(options("/auth/login").header("Origin",FRONTEND+".evil.example").header("Access-Control-Request-Method","POST"))
   .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
 }
 @Test void respuestaPublicaIncluyeCors()throws Exception{
  mvc.perform(post("/auth/cors-check").header("Origin",FRONTEND)).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin",FRONTEND));
 }
 @Test void permitirOrigenNoOmiteAutenticacion()throws Exception{
  mvc.perform(get("/api/crm/agencia/opciones").header("Origin",FRONTEND)).andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin",FRONTEND));
 }
}
