package com.marketing.agencia.administrador.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Email @Size(max = 140) String correo,
        @NotBlank @Size(min = 9) String telefono
){
}
