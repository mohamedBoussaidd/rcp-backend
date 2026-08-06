package com.remipreparateur.performance.referentiel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Niveau de compétition d'un référentiel : N1, N2, R1, U19N…
 *
 * <p>Seul élément de vocabulaire ÉDITABLE par le super-admin, et volontairement le seul. Un
 * niveau n'est qu'un libellé de regroupement : en inventer un ne casse rien. Une {@link
 * com.remipreparateur.performance.referentiel.MetriqueCharge métrique}, elle, porte le nom d'une
 * colonne réelle de {@code donnee_gps}, et un {@link
 * com.remipreparateur.performance.referentiel.PosteReference poste} dépend d'un rabattement 11 → 6
 * codé en dur — les rendre modifiables en base permettrait de créer une entrée que rien ne sait
 * calculer, et l'erreur serait silencieuse.
 *
 * <p>Existe surtout pour remplacer le texte libre : sans cette table, le catalogue finit avec
 * « N1 », « n1 » et « National 1 » comme trois niveaux distincts.
 */
@Entity
@Table(name = "niveau_competition")
@Getter
@Setter
public class NiveauCompetition {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
