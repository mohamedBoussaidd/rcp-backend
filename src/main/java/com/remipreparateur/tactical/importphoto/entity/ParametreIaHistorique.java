package com.remipreparateur.tactical.importphoto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/** Version historisée d'un paramètre IA (restauration possible depuis l'écran super-admin). */
@Entity
@Table(name = "parametre_ia_historique")
@Getter
@Setter
public class ParametreIaHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cle", nullable = false, length = 60)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;
}
