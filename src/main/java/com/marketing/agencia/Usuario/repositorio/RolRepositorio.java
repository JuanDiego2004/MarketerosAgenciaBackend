package com.marketing.agencia.Usuario.repositorio;

import com.marketing.agencia.Usuario.entidad.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepositorio extends JpaRepository<Rol, Long> {
 Optional<Rol> findByNombreRol( String nombreRol);


}
