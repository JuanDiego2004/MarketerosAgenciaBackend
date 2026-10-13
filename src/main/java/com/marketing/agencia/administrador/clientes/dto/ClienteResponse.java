package com.marketing.agencia.administrador.clientes.dto;

import java.time.OffsetDateTime;

public record ClienteResponse(
      Long idCliente,
      String nombre,
      String correo,
      String telefono,
      String estado,
      OffsetDateTime createdAt
) {
}
