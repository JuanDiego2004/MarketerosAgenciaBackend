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
    private final MapeoClienteResponse mapeoClienteResponse;

    private OffsetDateTime fechaActual() {
        return OffsetDateTime.now(ZoneId.of("America/Lima"));
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private Cliente buscarClienteActivo(Long id) {
        Cliente cliente = clienteRepositorio.findById(id)
                .orElseThrow(() -> new ExcepcionApi(
                        HttpStatus.NOT_FOUND,
                        "CLIENTE_NO_ENCONTRADO",
                        "No existe un cliente con el ID " + id
                ));

        if ("INACTIVO".equalsIgnoreCase(cliente.getEstado())) {
            throw new ExcepcionApi(
                    HttpStatus.NOT_FOUND,
                    "CLIENTE_NO_ENCONTRADO",
                    "No existe un cliente activo con el ID " + id
            );
        }

        return cliente;
    }

    @Transactional
    public List<ClienteResponse> listar() {
        return clienteRepositorio.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(cliente ->
                        !"INACTIVO".equalsIgnoreCase(cliente.getEstado()))
                .map(mapeoClienteResponse::mapearCreado)
                .toList();
    }

    @Transactional
    public ClienteResponse buscarPorId(Long id) {
        return mapeoClienteResponse.mapearCreado(
                buscarClienteActivo(id)
        );
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request) {
        String correo = normalizarCorreo(request.correo());

        if (clienteRepositorio.existsByCorreo(correo)) {
            throw new ExcepcionApi(
                    HttpStatus.CONFLICT,
                    "CLIENTE_CORREO_DUPLICADO",
                    "Ya existe un cliente registrado con ese correo"
            );
        }

        OffsetDateTime ahora = fechaActual();

        Cliente cliente = new Cliente();
        cliente.setNombre(request.nombre().trim());
        cliente.setCorreo(correo);
        cliente.setTelefono(request.telefono().trim());
        cliente.setEstado("ACTIVO");
        cliente.setCreatedAt(ahora);
        cliente.setUpdatedAt(ahora);

        Cliente guardado = clienteRepositorio.saveAndFlush(cliente);

        return mapeoClienteResponse.mapearCreado(guardado);
    }

    @Transactional
    public ClienteResponse actualizar(
            Long id,
            ClienteRequest request) {

        Cliente cliente = buscarClienteActivo(id);
        String correo = normalizarCorreo(request.correo());

        if (clienteRepositorio.existsByCorreoAndIdClienteNot(
                correo, id)) {

            throw new ExcepcionApi(
                    HttpStatus.CONFLICT,
                    "CLIENTE_CORREO_DUPLICADO",
                    "El correo ya está registrado por otro cliente"
            );
        }

        cliente.setNombre(request.nombre().trim());
        cliente.setCorreo(correo);
        cliente.setTelefono(request.telefono().trim());
        cliente.setUpdatedAt(fechaActual());

        Cliente actualizado = clienteRepositorio.saveAndFlush(cliente);

        return mapeoClienteResponse.mapearCreado(actualizado);
    }

    @Transactional
    public void eliminar(Long id) {
        Cliente cliente = buscarClienteActivo(id);

        cliente.setEstado("INACTIVO");
        cliente.setUpdatedAt(fechaActual());

        clienteRepositorio.saveAndFlush(cliente);
    }
}