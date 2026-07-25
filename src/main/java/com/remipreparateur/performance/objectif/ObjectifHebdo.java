package com.remipreparateur.performance.objectif;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Objectif hebdomadaire de charge (distance/joueur) d'une équipe, fixé par le préparateur.
 * Une seule valeur COURANTE par équipe, reconduite chaque semaine jusqu'à modification.
 * La « suggestion intelligente » (charge cible A.5) reste calculée à la volée côté Python.
 */
@Entity
@Table(name = "objectif_hebdo")
@Getter
@Setter
public class ObjectifHebdo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false, unique = true)
    private UUID equipeId;

    @Column(name = "objectif_distance_m")
    private Integer objectifDistanceM;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
