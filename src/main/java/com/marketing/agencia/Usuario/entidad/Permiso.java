package com.marketing.agencia.Usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "permiso")
@Data
public class Permiso {

    @Id
    @Column(name = "id_permiso")
    private Long idPermiso;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "modulo")
    private String modulo;
}