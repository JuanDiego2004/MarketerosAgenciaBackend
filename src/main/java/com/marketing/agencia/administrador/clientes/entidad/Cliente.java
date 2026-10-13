package com.marketing.agencia.administrador.clientes.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cliente")
public class Cliente {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      @Column(name = "id_cliente")
      private Long idCliente;
      private String nombre;
      private String correo;
      private String telefono;
      private String estado;
      @Column(name = "created_at")
      private OffsetDateTime createdAt;
      @Column(name = "updated_at")
      private OffsetDateTime updatedAt;
}
