package com.remipreparateur.saison.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase typée d'une saison. Le {@code type} pilote le PROFIL de surveillance appliqué
 * côté analytics Python (ACWR actif en compétition, neutralisé en préparation, silence
 * en trêve, etc.).
 */
@Entity
@Table(name = "periode_saison")
@Getter
@Setter
public class PeriodeSaison {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "saison_id", nullable = false)
    private UUID saisonId;

    /** Équipe à laquelle s'applique cette période dans la saison (cf. V37). */
    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    /** PREPARATION | COMPETITION | TREVE | REPRISE | INTERSAISON */
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "libelle")
    private String libelle;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "ordre", nullable = false)
    private short ordre = 0;
}
