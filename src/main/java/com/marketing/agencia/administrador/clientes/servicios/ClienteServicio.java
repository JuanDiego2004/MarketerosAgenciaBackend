package com.marketing.agencia.administrador.clientes.servicios;

import com.marketing.agencia.administrador.clientes.dto.ClienteRequest;
import com.marketing.agencia.administrador.clientes.dto.ClienteResponse;
import com.marketing.agencia.administrador.clientes.entidad.Cliente;
import com.marketing.agencia.administrador.clientes.mapper.MapeoClienteResponse;
import com.marketing.agencia.administrador.clientes.repositorio.ClienteRepositorio;
import com.marketing.agencia.config.ExcepcionApi;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClienteServicio {
    private final ClienteRepositorio clienteRepositorio;
    public final  MapeoClienteResponse mapeoClienteResponse;

    @Transactional
    public ClienteResponse crear(ClienteRequest request)   {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        if(clienteRepositorio.existsByCorreo(correo)) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Correoya reistrado", "Cliente no existe");

        }

        Cliente cliente = new Cliente();
        cliente.setNombre(request.nombre());
        cliente.setCorreo(request.correo());
        cliente.setTelefono(request.telefono());
        cliente.setEstado("ACTIVO");
        OffsetDateTime fechaActual = OffsetDateTime.now(ZoneId.of("America/Lima"));
        cliente.setCreatedAt(fechaActual);
        cliente.setUpdatedAt(fechaActual);
        Cliente guardado = clienteRepositorio.saveAndFlush(cliente);
        return mapeoClienteResponse.mapearCreado(guardado);
    }

    @Transactional
    public List<ClienteResponse> listar() {
        return clienteRepositorio.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(mapeoClienteResponse::mapearCreado)
                .toList();
    }
}
