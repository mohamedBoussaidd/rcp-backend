package com.remipreparateur.performance.referentiel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Une case du référentiel : pour un poste, un contexte et une métrique, la fourchette attendue.
 *
 * <p>Table volontairement PLATE (poste et contexte portés ici plutôt que par une table
 * intermédiaire) : une seule jointure pour tout lire, et ajouter une métrique reste une entrée
 * d'enum sans migration.
 *
 * <p>Deux contextes seulement, et le second inclut le premier — c'est la lecture du document de
 * référence (« la semaine représente 2,8 à 3,5 fois la distance du match ») :
 * <ul>
 *   <li>{@link #CONTEXTE_MATCH} — un match de 90 minutes ;</li>
 *   <li>{@link #CONTEXTE_SEMAINE} — une semaine type de compétition, <b>match compris</b>.</li>
 * </ul>
 * L'entraînement n'est donc PAS stocké : il se DÉRIVE (semaine − minutes réellement jouées).
 * C'est ce qui fait qu'un joueur resté sur le banc voit sa cible d'entraînement monter toute
 * seule, au lieu de disparaître des radars.
 *
 * <p>Pour une métrique d'{@link com.remipreparateur.performance.referentiel.MetriqueCharge.Nature#EXPOSITION
 * exposition}, {@code valeurMin} porte le pourcentage du record personnel attendu et
 * {@code valeurMax} reste vide : il n'y a pas de borne haute à courir vite.
 */
@Entity
@Table(name = "referentiel_objectif_valeur",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"referentiel_id", "poste", "contexte", "metrique"}))
@Getter
@Setter
public class ReferentielObjectifValeur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "referentiel_id", nullable = false)
    private UUID referentielId;

    /** Code de {@link com.remipreparateur.performance.referentiel.PosteReference}. */
    @Column(name = "poste", nullable = false)
    private String poste;

    /** MATCH | SEMAINE (contrainte CHECK en base). */
    @Column(name = "contexte", nullable = false)
    private String contexte;

    /** Code de {@link com.remipreparateur.performance.referentiel.MetriqueCharge}. */
    @Column(name = "metrique", nullable = false)
    private String metrique;

    /** Borne basse, ou seuil d'exposition en % du record personnel. */
    @Column(name = "valeur_min")
    private Integer valeurMin;

    /** Borne haute ; vide pour une métrique d'exposition. */
    @Column(name = "valeur_max")
    private Integer valeurMax;

    public static final String CONTEXTE_MATCH   = "MATCH";
    public static final String CONTEXTE_SEMAINE = "SEMAINE";

    /**
     * Milieu de fourchette — la valeur unique à utiliser quand il en faut une seule (base des
     * pourcentages d'un modèle de préparation, par exemple). Tolère une borne manquante.
     */
    public Integer valeurPivot() {
        if (valeurMin != null && valeurMax != null) return (valeurMin + valeurMax) / 2;
        return valeurMin != null ? valeurMin : valeurMax;
    }
}
