package com.remipreparateur.saison.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saison sportive d'un CLUB (ex. « 2026-2027 »). Cadre temporel qui borne tous les
 * calculs de charge/risque ; les périodes typées et l'effectif sont définis PAR ÉQUIPE
 * à l'intérieur de la saison. Au plus une saison {@code EN_COURS} par club (index unique
 * partiel, cf. V37).
 */
@Entity
@Table(name = "saison")
@Getter
@Setter
public class Saison {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /** PREPARATION | EN_COURS | CLOTUREE */
    @Column(name = "statut", nullable = false)
    private String statut = "EN_COURS";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
