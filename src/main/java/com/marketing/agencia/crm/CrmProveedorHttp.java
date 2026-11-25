package com.marketing.agencia.crm;

import com.fasterxml.jackson.databind.*;
import com.marketing.agencia.config.ExcepcionApi;
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
    private static final Set<String> HOSTS=Set.of("graph.facebook.com","api.mockaroo.com","api.brevo.com");
    public JsonNode post(String url,Map<String,String>cabeceras,Object cuerpo,boolean formulario){
        URI u=URI.create(url);if(!"https".equals(u.getScheme())||!HOSTS.contains(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1)throw invalido("Destino de integración no admitido");
        try{
            String contenido=formulario?form((Map<?,?>)cuerpo):json.writeValueAsString(cuerpo);
            var peticion=HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(30)).header("Content-Type",formulario?"application/x-www-form-urlencoded":"application/json");
            cabeceras.forEach(peticion::header);
            var r=cliente.send(peticion.POST(HttpRequest.BodyPublishers.ofString(contenido,StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300)throw rechazo(u,r.statusCode(),r.body());
            if(r.body().length()>4_000_000)throw invalido("Respuesta demasiado grande. Reduce el período.");
            return json.readTree(r.body());
        }catch(com.marketing.agencia.config.ExcepcionApi e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw invalido("Operación interrumpida; revisa el resultado en el proveedor antes de repetirla");}catch(Exception e){throw invalido("No se pudo confirmar la respuesta del proveedor");}
    }
    private ExcepcionApi rechazo(URI destino,int status,String cuerpo){
        if(!"graph.facebook.com".equals(destino.getHost()))return invalido("El proveedor rechazó la operación (HTTP "+status+"). Revisa la configuración del proveedor.");
        String codigo="";String mensaje="";
        try{
            JsonNode error=json.readTree(cuerpo).path("error");
            JsonNode valorCodigo=error.path("code");
            if(valorCodigo.isNumber()||valorCodigo.isTextual()){
                String candidato=valorCodigo.asText();if(candidato.matches("[0-9]{1,12}"))codigo=candidato;
            }
            JsonNode valorMensaje=error.path("message");if(valorMensaje.isTextual())mensaje=valorMensaje.asText();
        }catch(Exception ignored){/* No se expone el cuerpo sin formato del proveedor. */}
        mensaje=mensaje.replaceAll("[\\p{Cntrl}]"," ").replaceAll("(?i)(access[_ ]?token|authorization|bearer)\\s*[:=]?\\s*[^\\s,;]+","[credencial ocultada]").trim();
        if(mensaje.length()>240)mensaje=mensaje.substring(0,240)+"…";
        String detalle="Meta rechazó el mensaje (HTTP "+status+(codigo.isBlank()?"":", código "+codigo)+")"+(mensaje.isBlank()?".":": "+mensaje);
        return invalido(detalle);
    }
    static String form(Map<?,?>d){return d.entrySet().stream().map(e->URLEncoder.encode(e.getKey().toString(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue().toString(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
}
