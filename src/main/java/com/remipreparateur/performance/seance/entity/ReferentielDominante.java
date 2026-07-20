package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Référentiel global (seedé en V61) des dominantes de séance. {@code famille} :
 * SEANCE = registres généraux (technique, tactique…), ATHLETIQUE = qualités physiques
 * (vivacité, puissance aérobie…). Paramétrage par club = backlog v2.
 */
@Entity
@Table(name = "referentiel_dominante")
@Getter
@Setter
public class ReferentielDominante {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "famille", nullable = false, length = 12)
    private String famille;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;
}
