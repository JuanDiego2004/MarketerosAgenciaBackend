package com.marketing.agencia.administrador.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 140, message = "El correo no puede superar 140 caracteres")
        String correo,

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(min = 9, max = 20,
                message = "El teléfono debe tener entre 9 y 20 caracteres")
        String telefono

) {
}