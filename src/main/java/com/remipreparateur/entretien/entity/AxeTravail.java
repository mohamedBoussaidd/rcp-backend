package com.remipreparateur.entretien.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Axe de travail suivi pour un joueur (ex. « jeu de tête », « prise d'information »).
 * Rattaché à la FICHE joueur ({@code joueurId}) et au club, pas au compte utilisateur.
 * {@code categorie} : TECHNIQUE | TACTIQUE | MENTAL | PHYSIQUE.
 * {@code statut}    : EN_COURS | ACQUIS | ABANDONNE.
 */
@Entity
@Table(name = "axe_travail")
@Getter
@Setter
public class AxeTravail {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "categorie", nullable = false)
    private String categorie;

    @Column(name = "statut", nullable = false)
    private String statut = "EN_COURS";

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
