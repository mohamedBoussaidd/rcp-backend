package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Décision prise sur une semaine à deux matchs (ou plus).
 *
 * <p>La cible hebdomadaire du référentiel <b>inclut</b> le match : une deuxième rencontre ne
 * relève donc pas mécaniquement la semaine, elle mange la part d'entraînement. Trois réponses
 * sont défendables et l'application n'en choisit aucune à la place de l'entraîneur :
 * {@code ALLEGER} (défaut proposé — cible inchangée, l'entraînement encaisse), {@code ASSUMER}
 * (cible relevée d'un match) et {@code RELISSER} (cible réduite, différence reportée sur les
 * deux semaines suivantes via {@link ArbitrageSemaineReport}).
 *
 * <p>Une décision par équipe et par semaine, ancrée sur le <b>lundi</b> — la même borne que le
 * panneau « Objectif de la semaine » et que {@code objectif_periode_valeur.date_lundi}, sans
 * quoi les deux lectures ne porteraient pas sur les mêmes jours.
 */
@Entity
@Table(name = "arbitrage_semaine",
       uniqueConstraints = @UniqueConstraint(columnNames = {"equipe_id", "date_lundi"}))
@Getter
@Setter
public class ArbitrageSemaine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    /** Lundi de la semaine ISO arbitrée. */
    @Column(name = "date_lundi", nullable = false)
    private LocalDate dateLundi;

    /** ALLEGER | ASSUMER | RELISSER. */
    @Column(name = "choix", nullable = false, length = 20)
    private String choix;

    /**
     * Nombre de matchs constaté au moment de la décision. Si le calendrier bouge ensuite (match
     * reporté), l'écran peut dire que l'arbitrage ne correspond plus à la semaine réelle plutôt
     * que d'appliquer en silence un report devenu sans objet.
     */
    @Column(name = "nb_matchs", nullable = false)
    private Short nbMatchs = 2;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
