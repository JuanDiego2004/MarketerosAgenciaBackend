package com.marketing.agencia.Usuario.repositorio;

import com.marketing.agencia.Usuario.entidad.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);
    Optional<Usuario> findByIdSupabase(UUID idSupabase);
    boolean existsByCorreo(String correo);
}
