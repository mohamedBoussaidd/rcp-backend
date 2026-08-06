package com.remipreparateur.performance.referentiel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ADOPTION d'un référentiel par un club, éventuellement par équipe.
 *
 * <p>C'est cette table qui rend le référentiel utile TOUT SEUL, avant même qu'un objectif de
 * période n'existe : dès qu'un club a adopté, la colonne « Attendu » s'affiche partout à côté de
 * l'« Habituel » calculé sur l'historique du joueur. Afficher « Habituel 24 km / Attendu N1
 * 31–36 km » est déjà la fonction qui manquait le plus.
 *
 * <p>Deux niveaux, du plus général au plus précis :
 * <ul>
 *   <li>{@code equipeId == null} — le référentiel par défaut du club ;</li>
 *   <li>{@code equipeId != null} — la surcharge d'UNE équipe.</li>
 * </ul>
 * Un club a des séniors en N1, une réserve en régional et des U19 : il ne lui faut pas UNE
 * surcharge de club, il lui faut un référentiel DIFFÉRENT par équipe.
 *
 * <p>Le club est <b>épinglé sur une version</b> : publier une v2 du référentiel ne déplace rien
 * ici. La migration est un geste volontaire, proposé avec son écart.
 */
@Entity
@Table(name = "club_referentiel",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "equipe_id"}))
@Getter
@Setter
public class ClubReferentiel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** {@code null} = adoption par défaut du club ; renseigné = surcharge de cette équipe. */
    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "referentiel_id", nullable = false)
    private UUID referentielId;

    @Column(name = "adopte_par")
    private UUID adoptePar;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
