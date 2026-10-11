package com.marketing.agencia.Auth.controladores;

import com.marketing.agencia.Auth.dto.response.ApiRespuesta;
import com.marketing.agencia.Auth.dto.response.SesionRespuesta;
import com.marketing.agencia.Auth.servicios.AuthServicio;
import com.marketing.agencia.Usuario.dto.Requests.LoginRequest;
import com.marketing.agencia.Usuario.dto.Requests.RegistroRequest;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth"})
@RequiredArgsConstructor
public class AuthControler {
    private final AuthServicio authServicio;

   @PostMapping("/registro")
   public ResponseEntity<ApiRespuesta<SesionRespuesta>> registro(
           @Valid @RequestBody RegistroRequest request
           ) {
       return ResponseEntity.ok(ApiRespuesta.exito("Registro exitoso", authServicio.registro(request)));
   }

   @PostMapping("/login")
    public ResponseEntity<ApiRespuesta<SesionRespuesta>> login(
           @Valid @RequestBody LoginRequest request
           ) {
       return ResponseEntity.ok(ApiRespuesta.exito("Inicio de sesión exitoso", authServicio.login(request)));
   }
}
