package com.marketing.agencia.Usuario.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EditarUsuarioRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotNull Long idRol
) {}
