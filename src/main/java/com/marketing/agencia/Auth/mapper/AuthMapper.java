package com.marketing.agencia.Auth.mapper;

import com.marketing.agencia.Usuario.dto.Requests.RegistroRequest;
import com.marketing.agencia.Usuario.entidad.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")

public interface AuthMapper {
    @Mapping(target="idUsuario", ignore = true)
    @Mapping(target="idSupabase", ignore = true)
    @Mapping(target ="passwordHash", ignore=true)
    @Mapping(target="debeCambiarPassword", ignore=true)
    @Mapping(target="rol", ignore = true)
    @Mapping(target="estado", ignore = true)
    @Mapping(target="createdAt", ignore = true)
    Usuario toUsuario(RegistroRequest request);
}
