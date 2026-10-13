package com.marketing.agencia.administrador.clientes.mapper;

import com.marketing.agencia.administrador.clientes.dto.ClienteRequest;
import com.marketing.agencia.administrador.clientes.entidad.Cliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")

public interface clienteMapper {
    @Mapping(target = "estado", ignore = true)
    @Mapping(target="createdAt", ignore = true)
    @Mapping(target="updatedAt", ignore = true)
    Cliente toCliente(ClienteRequest request);
}
