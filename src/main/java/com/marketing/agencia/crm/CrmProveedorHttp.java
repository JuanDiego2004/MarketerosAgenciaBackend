package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.invalido;

/** Destinos fijos, sin redirecciones ni reintentos: nunca registra credenciales o respuestas. */
@Component @RequiredArgsConstructor
public class CrmProveedorHttp {
    private final ObjectMapper json;
    private final HttpClient cliente=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Set<String> HOSTS=Set.of("oauth2.googleapis.com","googleads.googleapis.com","analyticsdata.googleapis.com","graph.facebook.com","api.twilio.com");
    public JsonNode post(String url,Map<String,String>cabeceras,Object cuerpo,boolean formulario){
        URI u=URI.create(url);if(!"https".equals(u.getScheme())||!HOSTS.contains(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1)throw invalido("Destino de integración no admitido");
        try{
            String contenido=formulario?form((Map<?,?>)cuerpo):json.writeValueAsString(cuerpo);
            var peticion=HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(30)).header("Content-Type",formulario?"application/x-www-form-urlencoded":"application/json");
            cabeceras.forEach(peticion::header);
            var r=cliente.send(peticion.POST(HttpRequest.BodyPublishers.ofString(contenido,StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300)throw invalido("El proveedor rechazó la operación (HTTP "+r.statusCode()+"). Revisa configuración y permisos.");
            if(r.body().length()>4_000_000)throw invalido("Respuesta demasiado grande. Reduce el período.");
            return json.readTree(r.body());
        }catch(com.marketing.agencia.config.ExcepcionApi e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw invalido("Operación interrumpida; revisa el resultado en el proveedor antes de repetirla");}catch(Exception e){throw invalido("No se pudo confirmar la respuesta del proveedor");}
    }
    static String form(Map<?,?>d){return d.entrySet().stream().map(e->URLEncoder.encode(e.getKey().toString(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue().toString(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
}
