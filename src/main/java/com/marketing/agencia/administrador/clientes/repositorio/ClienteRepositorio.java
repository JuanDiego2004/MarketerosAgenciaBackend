package com.marketing.agencia.administrador.clientes.repositorio;

import com.marketing.agencia.administrador.clientes.entidad.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepositorio
        extends JpaRepository<Cliente, Long> {

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdClienteNot(
            String correo,
            Long idCliente
    );

    List<Cliente> findAllByOrderByCreatedAtDesc();
}