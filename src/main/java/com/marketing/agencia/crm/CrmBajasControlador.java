package com.marketing.agencia.crm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CrmBajasControlador {
    private final CrmBajasServicio servicio;
    @GetMapping(value="/api/bajas/{token}",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmar(@PathVariable String token){
        // GET no cambia datos: los escáneres de enlaces de email no dan de baja al destinatario.
        if(!token.matches("[A-Za-z0-9_-]{43}"))return pagina("Enlace no disponible","Revisa el enlace recibido.");
        return ResponseEntity.ok().headers(cabeceras()).body("<!doctype html><html lang='es'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Baja de comunicaciones</title><body><main><h1>Dejar de recibir marketing en este canal</h1><p>Confirma para bloquear futuras comunicaciones de marketing de esta agencia en el canal del enlace.</p><form method='post' action='/api/bajas/"+token+"'><button type='submit'>Confirmar baja</button></form></main></body></html>");
    }
    @PostMapping(value="/api/bajas/{token}",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> baja(@PathVariable String token){servicio.revocar(token);return pagina("Solicitud procesada","Si el enlace corresponde a una suscripción, ya no recibirá nuevos mensajes de marketing en este canal. Un mensaje que ya estuviera en tránsito puede llegar igualmente.");}
    private ResponseEntity<String> pagina(String titulo,String texto){return ResponseEntity.ok().headers(cabeceras()).body("<!doctype html><html lang='es'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+titulo+"</title><main><h1>"+titulo+"</h1><p>"+texto+"</p></main></html>");}
    private HttpHeaders cabeceras(){HttpHeaders h=new HttpHeaders();h.setCacheControl("no-store");h.set("Referrer-Policy","no-referrer");h.set("X-Robots-Tag","noindex, nofollow");return h;}
}
