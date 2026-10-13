package com.marketing.agencia.administrador.clientes.mapper;

import com.marketing.agencia.administrador.clientes.dto.ClienteResponse;
import com.marketing.agencia.administrador.clientes.entidad.Cliente;
import org.springframework.stereotype.Component;

@Component
public class MapeoClienteResponse {

    public ClienteResponse mapearCreado(Cliente cliente) {
        return new ClienteResponse(cliente.getIdCliente(), cliente.getNombre(), cliente.getCorreo(), cliente.getTelefono(), cliente.getEstado(), cliente.getCreatedAt());
    }
}
