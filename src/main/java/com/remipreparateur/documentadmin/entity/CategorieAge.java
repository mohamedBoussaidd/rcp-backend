package com.remipreparateur.documentadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Catégorie d'âge configurable par club (ex. U15, U17...). Les bornes sont exprimées en ÂGE
 * ATTEINT DANS LA SAISON (pas en année de naissance figée) : {@code age = anneeDebutSaison -
 * anneeNaissance(joueur)}. Ne nécessite donc aucune retouche d'une saison à l'autre — seulement
 * en cas de vraie réforme fédérale des catégories.
 */
@Entity
@Table(name = "categorie_age")
@Getter
@Setter
public class CategorieAge {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    /** Âge atteint dans la saison (inclusif) ; null = pas de plancher. */
    @Column(name = "age_min")
    private Short ageMin;

    /** Âge atteint dans la saison (inclusif) ; null = pas de plafond (ex. Senior). */
    @Column(name = "age_max")
    private Short ageMax;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
