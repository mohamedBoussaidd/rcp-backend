package com.remipreparateur.club.pack.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Surcharge d'activation d'un module POUR UN CLUB, par-dessus son pack.
 * {@code actif=true} = add-on (module activé en plus du pack) ; {@code actif=false} = retrait
 * explicite. Une surcharge l'emporte toujours sur le pack.
 */
@Entity
@Table(name = "club_module")
@IdClass(ClubModuleId.class)
@Getter
@Setter
public class ClubModule {

    @Id
    @Column(name = "club_id")
    private UUID clubId;

    @Id
    @Column(name = "module_code")
    private String moduleCode;

    @Column(nullable = false)
    private boolean actif;

    @Column(name = "maj_le", nullable = false)
    private LocalDateTime majLe = LocalDateTime.now();
}
