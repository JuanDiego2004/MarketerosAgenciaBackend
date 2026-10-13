package com.marketing.agencia.administrador.clientes.controlador;

import com.marketing.agencia.administrador.clientes.dto.ClienteRequest;
import com.marketing.agencia.administrador.clientes.dto.ClienteResponse;
import com.marketing.agencia.administrador.clientes.servicios.ClienteServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteControlador {
    private final ClienteServicio clienteServicio;

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse clienteCreado = clienteServicio.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteCreado);
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listar() {
        return ResponseEntity.ok(clienteServicio.listar());
    }
}
