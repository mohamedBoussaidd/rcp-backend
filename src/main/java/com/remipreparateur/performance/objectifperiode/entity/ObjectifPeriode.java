package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * L'INSTANCE : un modèle d'objectif posé sur une période précise de la saison.
 *
 * <p>C'est ici que la forme (le modèle) rencontre l'échelle (le référentiel) pour produire des
 * kilomètres. Une fois générées, les valeurs sont <b>figées et éditables case par case</b> :
 * corriger le modèle après coup ne rattrape pas les instances existantes, exactement comme une
 * diapo est un instantané là où un schéma partagé est une référence vivante. C'est délibéré — un
 * objectif déjà annoncé au groupe ne doit pas bouger dans le dos du préparateur.
 *
 * <p>La bascule d'une période à l'autre est GRATUITE : elle réutilise la résolution de période
 * déjà en place côté analytics ({@code contexte.py}, {@code WHERE :date BETWEEN date_debut AND
 * date_fin}). Rien de neuf à écrire pour qu'un 10 août fasse passer l'équipe de la trajectoire de
 * préparation aux cibles de championnat.
 */
@Entity
@Table(name = "objectif_periode",
       uniqueConstraints = @UniqueConstraint(columnNames = {"periode_id"}))
@Getter
@Setter
public class ObjectifPeriode {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** Période de saison sur laquelle porte cet objectif (une seule instance par période). */
    @Column(name = "periode_id", nullable = false)
    private UUID periodeId;

    /** Modèle d'origine — traçabilité et régénération ; peut devenir nul si le modèle est supprimé. */
    @Column(name = "modele_id")
    private UUID modeleId;

    /** Référentiel qui a servi d'échelle. Épinglé : republier une v2 ne recalcule rien ici. */
    @Column(name = "referentiel_id")
    private UUID referentielId;

    /**
     * Phases retenues à la génération, telles qu'affichées dans le bandeau de l'éditeur
     * (ex. {@code Accumulation:2|Développement:2|Pic:1|Décharge:1}). Conservé sous forme de
     * texte : c'est une TRACE de ce qui a été généré, pas une structure à ré-interroger.
     */
    @Column(name = "phases_resume", columnDefinition = "text")
    private String phasesResume;

    /** Message éventuel de la génération (phase supprimée faute de semaines, par exemple). */
    @Column(name = "avertissement", columnDefinition = "text")
    private String avertissement;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
