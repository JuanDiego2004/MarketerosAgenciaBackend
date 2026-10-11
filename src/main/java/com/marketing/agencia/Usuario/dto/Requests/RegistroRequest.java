package com.marketing.agencia.Usuario.dto.Requests;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class RegistroRequest {
    @NotBlank
    @Size(max = 120)
    private String nombres;

    @NotBlank
    @Email
    @Size(max = 150)
    private String correo;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
}
