package com.remipreparateur.entretien.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Évaluation d'un axe de travail au sein d'un entretien (jointure porteuse).
 * {@code note} 1..5 (nullable) + {@code tendance} EN_PROGRES | STAGNE | REGRESSE (nullable) +
 * commentaire. Unicité (entretien, axe) garantie en base.
 */
@Entity
@Table(name = "entretien_axe")
@Getter
@Setter
public class EntretienAxe {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "entretien_id", nullable = false)
    private UUID entretienId;

    @Column(name = "axe_travail_id", nullable = false)
    private UUID axeTravailId;

    @Column(name = "note")
    private Integer note;

    @Column(name = "tendance")
    private String tendance;

    @Column(name = "commentaire")
    private String commentaire;
}
