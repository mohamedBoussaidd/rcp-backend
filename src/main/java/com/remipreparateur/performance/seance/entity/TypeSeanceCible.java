package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Cibles d'équipe par type de séance, propres à un club (override par club).
 * Pré-remplissent le formulaire de séance ; modifiables séance par séance ensuite.
 */
@Entity
@Table(name = "type_seance_cible",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "type_seance_id"}))
@Getter
@Setter
public class TypeSeanceCible {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "type_seance_id", nullable = false)
    private UUID typeSeanceId;

    @Column(name = "objectif_distance_m")
    private Integer objectifDistanceM;

    @Column(name = "objectif_distance_haute_intensite_m")
    private Integer objectifDistanceHauteIntensiteM;

    @Column(name = "objectif_intensite")
    private Short objectifIntensite;
}
