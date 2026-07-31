package com.remipreparateur.performance.rpe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Effort perçu (Borg CR-10, 1..10) d'un joueur sur une séance.
 * seance_id référence soit `seance` (PHYSIQUE) soit `seance_technique` (TECHNIQUE)
 * — pas de FK dure ; la durée est snapshotée pour rendre la charge auto-suffisante.
 * charge = rpe × duree_minutes (calculée par le service).
 */
@Entity
@Table(name = "rpe_seance")
@Getter
@Setter
public class RpeSeance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "seance_type", nullable = false)
    private String seanceType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "rpe", nullable = false)
    private Short rpe;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "charge")
    private Integer charge;

    /** Niveau de plaisir ressenti sur la séance (1..10), issu du questionnaire post-séance. */
    @Column(name = "plaisir")
    private Short plaisir;

    @Column(name = "commentaire")
    private String commentaire;

    // ── Gêne déclarée APRÈS la séance (V91) ────────────────────────────────
    // Même vocabulaire que WellnessQuotidien, mais rattachée à SA séance : une gêne
    // du matin et une gêne d'entraînement peuvent coexister le même jour.

    /** Signalement de gêne localisée sur cette séance (null = aucune gêne). */
    @Column(name = "gene_zone")
    private String geneZone;

    @Column(name = "gene_intensite")
    private Short geneIntensite;

    /** Moment de la gêne : EFFORT | APRES | REPOS. */
    @Column(name = "gene_moment")
    private String geneMoment;

    /** Gêne marquée traitée par le staff (quitte les alertes, reste en historique). */
    @Column(name = "gene_traitee", nullable = false)
    private boolean geneTraitee = false;

    @Column(name = "gene_traitee_par")
    private UUID geneTraiteePar;

    @Column(name = "gene_traitee_le")
    private LocalDateTime geneTraiteeLe;

    /** Type de résolution une fois traitée : ARCHIVEE | CONVERTIE (null si non traitée). */
    @Column(name = "gene_resolution")
    private String geneResolution;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
