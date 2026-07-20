package com.remipreparateur.tactical.importphoto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/** Paramètre IA global (ex. prompt vision de l'import photo), éditable par le super-admin. */
@Entity
@Table(name = "parametre_ia")
@Getter
@Setter
public class ParametreIa {

    @Id
    @Column(name = "cle", length = 60)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private UUID updatedBy;
}
