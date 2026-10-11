package com.marketing.agencia.Usuario.entidad;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombres")
    private String nombres;

    @Column(name = "correo")
    private String correo;

    // Supabase Auth administra la contraseña; este campo se conserva por compatibilidad con el esquema.
    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @Column(name = "id_supabase", columnDefinition = "uuid", unique = true)
    private UUID idSupabase;

    @Column(name = "estado")
    private String estado;

    @Column(name = "debe_cambiar_password")
    private Boolean debeCambiarPassword;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol")
    private Rol rol;
}
