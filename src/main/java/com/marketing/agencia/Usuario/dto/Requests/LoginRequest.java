package com.marketing.agencia.Usuario.dto.Requests;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    @NotBlank
    @Email
    private String correo;

    @NotBlank
    private String contrasena;
}
